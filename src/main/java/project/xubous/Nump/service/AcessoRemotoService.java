package project.xubous.Nump.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import project.xubous.Nump.global.LinkIndisponivelException;
import project.xubous.Nump.model.AcessoRemoto;
import project.xubous.Nump.model.StatusAcessoRemoto;
import project.xubous.Nump.repository.AcessoRemotoRepository;

@Service
public class AcessoRemotoService
{
    private final AcessoRemotoRepository acessoRemotoRepository;
    private final Shortener shortener;
    private final WebhookPublisher webhookPublisher;

    // validade padrão do link de consentimento (rápido de propósito: é acesso à máquina)
    @Value ( "${app.acesso.expiracao-minutos:15}" )
    private int acessoExpiracaoMinutos;

    // Instalador do RustDesk que o script baixa na máquina do cliente
    @Value ( "${app.rustdesk.download-url:https://github.com/rustdesk/rustdesk/releases/download/1.5.0/rustdesk-1.5.0-x86_64.exe}" )
    private String rustdeskDownloadUrl;

    public AcessoRemotoService ( AcessoRemotoRepository acessoRemotoRepository,
                                 Shortener shortener,
                                 WebhookPublisher webhookPublisher )
    {
        this.acessoRemotoRepository = acessoRemotoRepository;
        this.shortener               = shortener;
        this.webhookPublisher        = webhookPublisher;
    }

    // ── Criação da sessão (técnico) ───────────────────────────────────────────

    public AcessoRemoto criarAcesso ( String clienteEmail, Integer expiraMinutos )
    {
        // token longo: o link dá acesso à máquina do cliente, não vale 8 chars
        String token = shortener.generateTokenSeguro ( );

        int validade = expiraMinutos != null ? expiraMinutos : acessoExpiracaoMinutos;

        LocalDateTime agora = LocalDateTime.now ( );

        AcessoRemoto acesso = new AcessoRemoto ( );
        acesso.setToken        ( token );
        acesso.setConsentUrl   ( shortener.generateConsentUrl ( token ) );
        acesso.setOwnerEmail   ( getCurrentUserEmail ( ) );
        acesso.setClienteEmail ( clienteEmail );
        acesso.setCreatedAt    ( agora );
        acesso.setExpiresAt    ( validade > 0 ? agora.plusMinutes ( validade ) : null );
        acesso.setStatus       ( StatusAcessoRemoto.PENDENTE );

        return acessoRemotoRepository.save ( acesso );
    }

    public List < AcessoRemoto > getAllAcessos ( )
    {
        return acessoRemotoRepository.findAllByOwnerEmailOrderByCreatedAtDesc ( getCurrentUserEmail ( ) );
    }

    public Optional < AcessoRemoto > getAcessoById ( Long id )
    {
        return acessoRemotoRepository.findByIdAndOwnerEmail ( id, getCurrentUserEmail ( ) );
    }

    @Transactional
    public void cancelarAcesso ( long id )
    {
        if ( ! acessoRemotoRepository.existsByIdAndOwnerEmail ( id, getCurrentUserEmail ( ) ) )
        {
            return;
        }

        acessoRemotoRepository.findByIdAndOwnerEmail ( id, getCurrentUserEmail ( ) )
                .ifPresent ( ( acesso ) ->
                {
                    // Pendente: só cancela (marca CANCELADO, mantém o histórico).
                    if ( acesso.getStatus ( ) == StatusAcessoRemoto.PENDENTE )
                    {
                        acesso.setStatus ( StatusAcessoRemoto.CANCELADO );
                        acessoRemotoRepository.save ( acesso );
                    }
                    // Já atendido (CONSENTIDO/EXPIRADO/CANCELADO): o técnico está
                    // limpando o painel — remove o registro de vez.
                    // Obs.: isto NÃO desinstala o RustDesk da máquina do cliente
                    // (o Nump não alcança ela) — quem faz isso é o
                    // "Encerrar atendimento.bat" rodado lá.
                    else
                    {
                        acessoRemotoRepository.delete ( acesso );
                    }
                } );
    }

    // ── Consentimento (cliente, rota pública) ─────────────────────────────────

    public Optional < AcessoRemoto > getAcessoByToken ( String token )
    {
        return acessoRemotoRepository.findByToken ( token );
    }

    // Registra o consentimento e devolve o que o n8n responder (ex.: ID do RustDesk).
    // Uso único: depois de consentido, o link não pode ser reutilizado.
    @Transactional
    public Map < String, Object > consentir ( String token, String ip, String userAgent, String clienteEmail )
    {
        AcessoRemoto acesso = acessoRemotoRepository.findByToken ( token )
                .orElseThrow ( ( ) -> new IllegalArgumentException ( "Link de acesso inválido" ) );

        if ( acesso.getStatus ( ) == StatusAcessoRemoto.CONSENTIDO )
        {
            throw new LinkIndisponivelException ( "Este link já foi utilizado" );
        }

        if ( acesso.getStatus ( ) == StatusAcessoRemoto.CANCELADO )
        {
            throw new LinkIndisponivelException ( "Este link foi cancelado pelo técnico" );
        }

        if ( acesso.getExpiresAt ( ) != null && acesso.getExpiresAt ( ).isBefore ( LocalDateTime.now ( ) ) )
        {
            acesso.setStatus ( StatusAcessoRemoto.EXPIRADO );
            acessoRemotoRepository.save ( acesso );
            throw new LinkIndisponivelException ( "Este link expirou" );
        }

        // ── Registra a trilha de auditoria do consentimento ────────────────────
        acesso.setConsentAt        ( LocalDateTime.now ( ) );
        acesso.setConsentIp        ( ip );
        acesso.setConsentUserAgent ( userAgent );
        if ( clienteEmail != null && !clienteEmail.isBlank ( ) )
        {
            acesso.setClienteEmail ( clienteEmail );
        }
        acesso.setStatus ( StatusAcessoRemoto.CONSENTIDO );

        acessoRemotoRepository.save ( acesso );

        // ── Avisa o n8n e lê a resposta (ID/servidor do RustDesk, etc.) ──────
        Map < String, Object > payload = new HashMap <> ( );
        payload.put ( "token", acesso.getToken ( ) );
        payload.put ( "consentUrl", acesso.getConsentUrl ( ) );
        payload.put ( "ownerEmail", acesso.getOwnerEmail ( ) );
        payload.put ( "clienteEmail", acesso.getClienteEmail ( ) );
        payload.put ( "consentAt", acesso.getConsentAt ( ).toString ( ) );
        payload.put ( "ip", ip );
        payload.put ( "userAgent", userAgent );

        Map < String, Object > respostaN8n = webhookPublisher.publicarESperar ( "acesso_consentido", payload );

        // ── Senha temporária: SEMPRE gerada pelo Nump ────────────────────────
        // É ela que o script aplica na máquina do cliente, dando acesso não
        // atendido (o técnico conecta sem o cliente aceitar de novo).
        // Aceitamos ID/servidor do n8n; a senha do n8n é ignorada de propósito.
        String rustdeskId     = texto ( respostaN8n, "rustdeskId", "rustdesk_id",
                                                 "anydeskId", "anydesk_id" );
        String rustdeskServer = texto ( respostaN8n, "rustdeskServer", "rustdesk_server" );
        String rustdeskSenha  = gerarSenhaTemporaria ( );

        acesso.setRustdeskId     ( rustdeskId );
        acesso.setRustdeskSenha  ( rustdeskSenha );
        acesso.setRustdeskServer ( rustdeskServer );

        // token de uso único que o .bat usa para buscar a senha na execução
        // (assim o arquivo baixado não sai com a senha em texto claro)
        acesso.setFerramentaToken   ( gerarTokenFerramenta ( ) );
        acesso.setFerramentaUsadaEm ( null );
        acessoRemotoRepository.save ( acesso );

        // payload devolvido à página do cliente
        Map < String, Object > resultado = new HashMap <> ( );
        resultado.put ( "autorizado", true );
        resultado.put ( "token", acesso.getToken ( ) );
        resultado.put ( "consentAt", acesso.getConsentAt ( ).toString ( ) );
        // o .bat usa este token de uso único para buscar a senha na execução
        resultado.put ( "ferramentaToken", acesso.getFerramentaToken ( ) );

        // dados RustDesk (a página exibe a senha; o ID vem do script)
        Map < String, Object > rustdesk = new HashMap <> ( );
        if ( rustdeskId != null )
        {
            rustdesk.put ( "id", rustdeskId );
        }
        rustdesk.put ( "senha", rustdeskSenha );
        if ( rustdeskServer != null )
        {
            rustdesk.put ( "server", rustdeskServer );
        }
        resultado.put ( "rustdesk", rustdesk );

        // se o n8n devolveu algo não mapeado, repassa o JSON cru para a página exibir
        if ( respostaN8n != null && !respostaN8n.isEmpty ( ) )
        {
            resultado.put ( "n8n", respostaN8n );
        }

        return resultado;
    }

    // ── Ferramenta RustDesk para o cliente baixar ─────────────────────────────

    // Gera o .bat que o cliente executa na máquina dele. Fluxo (UMA execução só):
    //   1. o .bat salva um script PowerShell embutido em %TEMP% e o roda ELEVADO
    //      com -Wait (o cliente clica 1x "Sim" no UAC e a mesma janela segue);
    //   2. o PowerShell baixa o RustDesk RÁPIDO (curl com progresso; fallback
    //      BITS/Invoke-WebRequest), instala em silêncio;
    //   3. garante o serviço (--install-service) e aplica a SENHA (--password),
    //      que é o que dá o acesso NÃO ATENDIDO (técnico conecta sem aceitar);
    //   4. abre o RustDesk, lê o ID (--get-id) e MOSTRA na tela para o cliente
    //      enviar ao técnico.
    // Antes o .bat se reexecutava sem -Wait e a janela do usuário fechava,
    // parecendo exigir "rodar 2 vezes". Agora é uma janela só, sem clicar de novo.
    public String gerarFerramenta ( String token )
    {
        AcessoRemoto acesso = acessoRemotoRepository.findByToken ( token )
                .orElseThrow ( ( ) -> new IllegalArgumentException ( "Link de acesso inválido" ) );

        if ( acesso.getStatus ( ) != StatusAcessoRemoto.CONSENTIDO )
        {
            throw new LinkIndisponivelException ( "Autorize o acesso antes de baixar a ferramenta" );
        }

        // valores sanitizados: só entram no .bat caracteres seguros (sem injeção)
        String url      = sanitizar ( rustdeskDownloadUrl );
        String senha    = sanitizar ( acesso.getRustdeskSenha ( ) );
        String servidor = sanitizar ( acesso.getRustdeskServer ( ) );

        // ── Script PowerShell embutido (roda ELEVADO, 1 janela) ──────────────
        StringBuilder ps = new StringBuilder ( );
        ps.append ( "$ErrorActionPreference = 'Continue'\r\n" );
        ps.append ( "$url    = '" + url + "'\r\n" );
        ps.append ( "$senha  = '" + senha + "'\r\n" );
        if ( servidor != null )
        {
            ps.append ( "$server = '" + servidor + "'\r\n" );
        }
        ps.append ( "$inst   = Join-Path $env:TEMP 'nump_rustdesk_setup.exe'\r\n" );
        ps.append ( "\r\n" );
        ps.append ( "function Titulo($t) { Write-Host ''; Write-Host ('  ' + $t) -ForegroundColor Cyan }\r\n" );
        ps.append ( "\r\n" );
        ps.append ( "Write-Host ''\r\n" );
        ps.append ( "Write-Host '  ============================================' -ForegroundColor Green\r\n" );
        ps.append ( "Write-Host '   Atendimento remoto seguro - Nump' -ForegroundColor Green\r\n" );
        ps.append ( "Write-Host '  ============================================' -ForegroundColor Green\r\n" );
        ps.append ( "\r\n" );
        // ── Download (curl com progresso; fallback BITS/Invoke-WebRequest) ────
        ps.append ( "Titulo 'Baixando o RustDesk (isso demora um pouco)...'\r\n" );
        ps.append ( "$baixou = $false\r\n" );
        ps.append ( "if (Test-Path $inst) { $baixou = $true }\r\n" );
        ps.append ( "if (-not $baixou) {\r\n" );
        ps.append ( "  try {\r\n" );
        ps.append ( "    $curlexe = (Get-Command curl.exe -ErrorAction SilentlyContinue).Source\r\n" );
        ps.append ( "    if ($curlexe) {\r\n" );
        ps.append ( "      & $curlexe -L --progress-bar -o $inst $url\r\n" );
        ps.append ( "      if (Test-Path $inst) { $baixou = $true }\r\n" );
        ps.append ( "    }\r\n" );
        ps.append ( "  } catch { }\r\n" );
        ps.append ( "}\r\n" );
        ps.append ( "if (-not $baixou) {\r\n" );
        ps.append ( "  try {\r\n" );
        ps.append ( "    Import-Module BitsTransfer -ErrorAction Stop\r\n" );
        ps.append ( "    Start-BitsTransfer -Source $url -Destination $inst\r\n" );
        ps.append ( "  } catch {\r\n" );
        ps.append ( "    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12\r\n" );
        ps.append ( "    Invoke-WebRequest -Uri $url -OutFile $inst -UseBasicParsing\r\n" );
        ps.append ( "  }\r\n" );
        ps.append ( "  if (Test-Path $inst) { $baixou = $true }\r\n" );
        ps.append ( "}\r\n" );
        ps.append ( "if (-not $baixou) { goto falha }\r\n" );
        ps.append ( "\r\n" );
        // ── Instala em silêncio ───────────────────────────────────────────────
        ps.append ( "Titulo 'Instalando o RustDesk (pode aparecer uma janela de progresso)...'\r\n" );
        ps.append ( "Start-Process -FilePath $inst -ArgumentList '--silent-install' -Wait\r\n" );
        ps.append ( "Start-Sleep -Seconds 8\r\n" );
        ps.append ( "\r\n" );
        // ── Localiza o rustdesk.exe (install elevado e por usuário) ───────────
        ps.append ( "$cands = @(\r\n" );
        ps.append ( "  (Join-Path $env:ProgramFiles 'RustDesk\\rustdesk.exe'),\r\n" );
        ps.append ( "  (Join-Path ${env:ProgramFiles(x86)} 'RustDesk\\rustdesk.exe'),\r\n" );
        ps.append ( "  (Join-Path $env:LOCALAPPDATA 'RustDesk\\rustdesk.exe'),\r\n" );
        ps.append ( "  (Join-Path $env:LOCALAPPDATA 'Programs\\RustDesk\\rustdesk.exe')\r\n" );
        ps.append ( ")\r\n" );
        ps.append ( "$app = $cands | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1\r\n" );
        ps.append ( "if (-not $app) { goto falha }\r\n" );
        ps.append ( "\r\n" );
        if ( servidor != null )
        {
            ps.append ( "$env:RUSTDESK_API_HOST = $server\r\n" );
        }
        // ── Serviço + senha de atendimento (acesso não atendido) ──────────────
        ps.append ( "Titulo 'Preparando o acesso...'\r\n" );
        ps.append ( "Start-Process -FilePath $app -ArgumentList '--install-service' -Wait -ErrorAction SilentlyContinue\r\n" );
        ps.append ( "Start-Sleep -Seconds 5\r\n" );
        ps.append ( "Start-Process -FilePath $app -ArgumentList ('--password', $senha) -Wait -ErrorAction SilentlyContinue\r\n" );
        ps.append ( "Start-Sleep -Seconds 3\r\n" );
        ps.append ( "\r\n" );
        // ── Abre o RustDesk e lê o ID ────────────────────────────────────────
        ps.append ( "Start-Process -FilePath $app -ErrorAction SilentlyContinue\r\n" );
        ps.append ( "Titulo 'Conectando ao servidor, aguarde o ID...'\r\n" );
        ps.append ( "$id = ''\r\n" );
        ps.append ( "for ($i = 0; $i -lt 15 -and -not $id; $i++) {\r\n" );
        ps.append ( "  Start-Sleep -Seconds 3\r\n" );
        ps.append ( "  try {\r\n" );
        ps.append ( "    $out = & $app --get-id 2>$null | Out-String\r\n" );
        ps.append ( "    if ($out -match '(\\d{6,})') { $id = $Matches[1] }\r\n" );
        ps.append ( "  } catch { }\r\n" );
        ps.append ( "}\r\n" );
        ps.append ( "\r\n" );
        ps.append ( "Write-Host ''\r\n" );
        ps.append ( "Write-Host '  --------------------------------------------' -ForegroundColor Yellow\r\n" );
        ps.append ( "if ($id) {\r\n" );
        ps.append ( "  Write-Host ('   ID do RustDesk: ' + $id) -ForegroundColor Yellow\r\n" );
        ps.append ( "} else {\r\n" );
        ps.append ( "  Write-Host '   Nao consegui ler o ID automaticamente.' -ForegroundColor Yellow\r\n" );
        ps.append ( "}\r\n" );
        ps.append ( "Write-Host '  --------------------------------------------' -ForegroundColor Yellow\r\n" );
        ps.append ( "Write-Host ''\r\n" );
        ps.append ( "if ($id) {\r\n" );
        ps.append ( "  Write-Host '  Envie este ID ao tecnico. NAO precisa aceitar nada:' -ForegroundColor White\r\n" );
        ps.append ( "  Write-Host '  a senha de atendimento ja esta aplicada.' -ForegroundColor White\r\n" );
        ps.append ( "  try { Set-Clipboard -Value $id; Write-Host '  (ID copiado para a area de transferencia)' -ForegroundColor DarkGray } catch { }\r\n" );
        ps.append ( "} else {\r\n" );
        ps.append ( "  Write-Host '  Abra o RustDesk e envie ao tecnico o ID exibido.' -ForegroundColor White\r\n" );
        ps.append ( "}\r\n" );
        ps.append ( "Write-Host '  Mantenha o RustDesk aberto ate o atendimento acabar.' -ForegroundColor White\r\n" );
        ps.append ( "goto fim\r\n" );
        ps.append ( "\r\n" );
        ps.append ( ":falha\r\n" );
        ps.append ( "Write-Host ''\r\n" );
        ps.append ( "Write-Host '  Nao foi possivel preparar o acesso remoto.' -ForegroundColor Red\r\n" );
        ps.append ( "Write-Host '  Baixe o RustDesk em https://rustdesk.com, instale,' -ForegroundColor Red\r\n" );
        ps.append ( "Write-Host '  abra e informe o ID exibido ao tecnico.' -ForegroundColor Red\r\n" );
        ps.append ( "\r\n" );
        ps.append ( ":fim\r\n" );
        ps.append ( "Write-Host ''\r\n" );
        ps.append ( "Write-Host '  Pressione uma tecla para fechar esta janela...' -ForegroundColor DarkGray\r\n" );
        ps.append ( "$null = $Host.UI.RawUI.ReadKey('NoEcho,IncludeKeyDown')\r\n" );

        // ── .bat: salva o PowerShell acima e o roda elevado, com -Wait ────────
        // -Wait mantém a janela original aberta até o script elevado terminar,
        // então o cliente NÃO precisa executar o arquivo de novo.
        String b64 = java.util.Base64.getEncoder ( ).encodeToString (
                ps.toString ( ).getBytes ( java.nio.charset.StandardCharsets.UTF_16LE ) );

        StringBuilder bat = new StringBuilder ( );
        bat.append ( "@echo off\r\n" );
        bat.append ( "title Atendimento remoto - Nump\r\n" );
        bat.append ( "chcp 65001 >nul\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  ============================================\r\n" );
        bat.append ( "echo   Atendimento remoto seguro - Nump\r\n" );
        bat.append ( "echo  ============================================\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  O Windows vai pedir permissao de administrador.\r\n" );
        bat.append ( "echo  Clique em SIM e aguarde (a instalacao comeca sozinha).\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "set \"PSS=%TEMP%\\nump_atendimento.ps1\"\r\n" );
        // Grava o script decodificando o base64 (UTF-16LE) via certutil.
        bat.append ( "set \"B64=%TEMP%\\nump_atendimento.b64\"\r\n" );
        bat.append ( "> \"%B64%\" echo " + b64 + "\r\n" );
        bat.append ( "certutil -f -decode \"%B64%\" \"%PSS%\" >nul 2>&1\r\n" );
        bat.append ( "del \"%B64%\" >nul 2>&1\r\n" );
        bat.append ( "if not exist \"%PSS%\" goto sempoder\r\n" );
        // Eleva e ESPERA (-Wait): uma única janela de trabalho, sem rodar 2x.
        // A janela original só sai quando o script elevado terminar.
        bat.append ( "powershell -NoProfile -ExecutionPolicy Bypass -Command \"Start-Process powershell -Verb RunAs -Wait -ArgumentList '-NoProfile','-ExecutionPolicy','Bypass','-File','%PSS%'\"\r\n" );
        bat.append ( "del \"%PSS%\" >nul 2>&1\r\n" );
        bat.append ( "exit /b 0\r\n" );
        bat.append ( "\r\n" );
        bat.append ( ":sempoder\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  Nao foi possivel preparar o script de atendimento.\r\n" );
        bat.append ( "echo  Baixe o RustDesk em https://rustdesk.com, instale,\r\n" );
        bat.append ( "echo  abra e informe o ID exibido ao tecnico.\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "pause\r\n" );
        bat.append ( "exit /b 1\r\n" );

        return bat.toString ( );
    }

    // Troca o token da ferramenta pela senha temporária (usado pelo .bat).
    // NÃO consome o token: o cliente pode rodar o .bat mais de uma vez dentro do
    // prazo da sessão (rerodar, antivírus executando 2x, etc.) sem cair em erro.
    // A senha continua FORA do arquivo baixado — só é obtida em runtime, e a
    // sessão já expira sozinha (expiresAt). A primeira busca fica registrada em
    // ferramentaUsadaEm (auditoria), mas não bloqueia as seguintes.
    @Transactional
    public String obterSenhaPorFerramentaToken ( String ferramentaToken )
    {
        AcessoRemoto acesso = acessoRemotoRepository.findByFerramentaToken ( ferramentaToken )
                .orElseThrow ( ( ) -> new LinkIndisponivelException ( "Link de ferramenta inválido" ) );

        if ( acesso.getStatus ( ) != StatusAcessoRemoto.CONSENTIDO )
        {
            throw new LinkIndisponivelException ( "Autorize o acesso antes de baixar a ferramenta" );
        }

        // registra a primeira utilização (auditoria), sem bloquear as demais
        if ( acesso.getFerramentaUsadaEm ( ) == null )
        {
            acesso.setFerramentaUsadaEm ( LocalDateTime.now ( ) );
            acessoRemotoRepository.save ( acesso );
        }

        return acesso.getRustdeskSenha ( );
    }

    // Gera o .bat de ENCERRAMENTO: o cliente roda ao fim do atendimento para
    // REVOGAR o acesso remoto desta sessão, sem desinstalar o RustDesk.
    // Limpa a senha temporária e para o serviço: o técnico não conecta mais
    // (a menos que gere um novo link), mas o RustDesk CONTINUA INSTALADO —
    // ideal para máquinas que serão acessadas de novo no futuro (o próximo
    // atendimento só reaplica a senha).
    public String gerarEncerramento ( String token )
    {
        AcessoRemoto acesso = acessoRemotoRepository.findByToken ( token )
                .orElseThrow ( ( ) -> new IllegalArgumentException ( "Link de acesso inválido" ) );

        if ( acesso.getStatus ( ) != StatusAcessoRemoto.CONSENTIDO )
        {
            throw new LinkIndisponivelException ( "Nenhum atendimento em andamento para este link" );
        }

        StringBuilder bat = new StringBuilder ( );
        bat.append ( "@echo off\r\n" );
        bat.append ( "chcp 65001 >nul\r\n" );
        bat.append ( "title Encerrar atendimento remoto - Nump\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  ============================================\r\n" );
        bat.append ( "echo   Encerrar atendimento remoto - Nump\r\n" );
        bat.append ( "echo  ============================================\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  Isso REVOGA o acesso remoto desta sessao:\r\n" );
        bat.append ( "echo  o tecnico nao podera mais se conectar.\r\n" );
        bat.append ( "echo  O RustDesk permanece instalado para futuros\r\n" );
        bat.append ( "echo  atendimentos (nada mais precisa ser baixado).\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "pause\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  Revogando o acesso remoto...\r\n" );
        bat.append ( "set \"APP=\"\r\n" );
        bat.append ( "if exist \"C:\\Program Files\\RustDesk\\rustdesk.exe\" set \"APP=C:\\Program Files\\RustDesk\\rustdesk.exe\"\r\n" );
        bat.append ( "if not defined APP if exist \"%LOCALAPPDATA%\\Programs\\RustDesk\\rustdesk.exe\" set \"APP=%LOCALAPPDATA%\\Programs\\RustDesk\\rustdesk.exe\"\r\n" );
        bat.append ( "if not defined APP if exist \"%ProgramFiles(x86)%\\RustDesk\\rustdesk.exe\" set \"APP=%ProgramFiles(x86)%\\RustDesk\\rustdesk.exe\"\r\n" );
        bat.append ( "if not defined APP goto processo\r\n" );
        // limpa a senha de acesso não atendido e para o serviço (mantém instalado)
        bat.append ( "start /wait \"\" \"%APP%\" --password \"\" >nul 2>&1\r\n" );
        bat.append ( "start /wait \"\" \"%APP%\" --uninstall-service >nul 2>&1\r\n" );
        bat.append ( ":processo\r\n" );
        bat.append ( "taskkill /f /im rustdesk.exe >nul 2>&1\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  Acesso remoto encerrado.\r\n" );
        bat.append ( "echo  O RustDesk continua instalado nesta maquina.\r\n" );
        bat.append ( "echo  Voce pode fechar esta janela.\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "pause\r\n" );
        bat.append ( "exit /b 0\r\n" );

        return bat.toString ( );
    }

    // Gera o .bat de DESINSTALAÇÃO COMPLETA (rota pública, exige consentimento).
    // Diferente do encerramento (que só revoga o acesso), aqui o RustDesk é
    // REMOVIDO por completo da máquina — indicado para máquinas que quase não
    // recebem acesso remoto. Usa o desinstalador oficial (`--uninstall`) e ainda
    // limpa as pastas de dados que o desinstalador do RustDesk costuma deixar.
    public String gerarDesinstalacao ( String token )
    {
        AcessoRemoto acesso = acessoRemotoRepository.findByToken ( token )
                .orElseThrow ( ( ) -> new IllegalArgumentException ( "Link de acesso inválido" ) );

        if ( acesso.getStatus ( ) != StatusAcessoRemoto.CONSENTIDO )
        {
            throw new LinkIndisponivelException ( "Nenhum atendimento em andamento para este link" );
        }

        StringBuilder bat = new StringBuilder ( );
        bat.append ( "@echo off\r\n" );
        bat.append ( "chcp 65001 >nul\r\n" );
        bat.append ( "title Desinstalar RustDesk - Nump\r\n" );
        bat.append ( "rem --- Desinstalacao completa: exige admin (senão não remove). ---\r\n" );
        bat.append ( "net session >nul 2>&1\r\n" );
        bat.append ( "if %errorlevel% neq 0 (\r\n" );
        bat.append ( "  echo.\r\n" );
        bat.append ( "  echo  O Nump vai pedir permissao de administrador.\r\n" );
        bat.append ( "  echo  Clique em SIM na janela do Windows para continuar.\r\n" );
        bat.append ( "  echo.\r\n" );
        bat.append ( "  powershell -NoProfile -Command \"Start-Process -FilePath '%~f0' -Verb RunAs\"\r\n" );
        bat.append ( "  exit /b\r\n" );
        bat.append ( ")\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  ============================================\r\n" );
        bat.append ( "echo   Desinstalar o RustDesk - Nump\r\n" );
        bat.append ( "echo  ============================================\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  Isso REMOVE o RustDesk por completo desta maquina.\r\n" );
        bat.append ( "echo  Use apenas se este computador quase nao recebe\r\n" );
        bat.append ( "echo  acesso remoto. Se for ser acessado de novo, prefira\r\n" );
        bat.append ( "echo  \"Encerrar atendimento\" (revoga o acesso e mantem).\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "pause\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  Parando o RustDesk...\r\n" );
        bat.append ( "taskkill /f /im rustdesk.exe >nul 2>&1\r\n" );
        bat.append ( "ping -n 3 127.0.0.1 >nul\r\n" );
        bat.append ( "set \"APP=\"\r\n" );
        bat.append ( "if exist \"C:\\Program Files\\RustDesk\\rustdesk.exe\" set \"APP=C:\\Program Files\\RustDesk\\rustdesk.exe\"\r\n" );
        bat.append ( "if not defined APP if exist \"%ProgramFiles(x86)%\\RustDesk\\rustdesk.exe\" set \"APP=%ProgramFiles(x86)%\\RustDesk\\rustdesk.exe\"\r\n" );
        bat.append ( "if not defined APP if exist \"%LOCALAPPDATA%\\Programs\\RustDesk\\rustdesk.exe\" set \"APP=%LOCALAPPDATA%\\Programs\\RustDesk\\rustdesk.exe\"\r\n" );
        bat.append ( "if not defined APP if exist \"%LOCALAPPDATA%\\RustDesk\\rustdesk.exe\" set \"APP=%LOCALAPPDATA%\\RustDesk\\rustdesk.exe\"\r\n" );
        bat.append ( "echo  Removendo o RustDesk...\r\n" );
        bat.append ( "if not defined APP goto limpar\r\n" );
        // desinstalador oficial: remove serviço, atalhos e arquivos do programa
        bat.append ( "start /wait \"\" \"%APP%\" --uninstall >nul 2>&1\r\n" );
        bat.append ( "ping -n 6 127.0.0.1 >nul\r\n" );
        bat.append ( ":limpar\r\n" );
        bat.append ( "rem --- O desinstalador oficial deixa pastas de dados para tras. ---\r\n" );
        bat.append ( "rem --- Aqui removemos tambem essas pastas (cache, config, ID). ---\r\n" );
        bat.append ( "rd /s /q \"%APPDATA%\\RustDesk\" >nul 2>&1\r\n" );
        bat.append ( "rd /s /q \"%LOCALAPPDATA%\\RustDesk\" >nul 2>&1\r\n" );
        bat.append ( "rd /s /q \"%APPDATA%\\..\\Local\\Programs\\RustDesk\" >nul 2>&1\r\n" );
        bat.append ( "rd /s /q \"%ProgramData%\\RustDesk\" >nul 2>&1\r\n" );
        bat.append ( "rd /s /q \"C:\\Program Files\\RustDesk\" >nul 2>&1\r\n" );
        bat.append ( "rd /s /q \"%ProgramFiles(x86)%\\RustDesk\" >nul 2>&1\r\n" );
        bat.append ( "rem --- config do servico (roda como SYSTEM) ---\r\n" );
        bat.append ( "rd /s /q \"%SystemRoot%\\System32\\config\\systemprofile\\AppData\\Roaming\\RustDesk\" >nul 2>&1\r\n" );
        bat.append ( "rd /s /q \"%SystemRoot%\\ServiceProfiles\\LocalService\\AppData\\Roaming\\RustDesk\" >nul 2>&1\r\n" );
        bat.append ( "rem --- ultima limpeza do servico, se ainda existir ---\r\n" );
        bat.append ( "sc stop rustdesk >nul 2>&1\r\n" );
        bat.append ( "sc delete rustdesk >nul 2>&1\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  RustDesk removido por completo.\r\n" );
        bat.append ( "echo  Voce pode fechar esta janela.\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "pause\r\n" );
        bat.append ( "exit /b 0\r\n" );

        return bat.toString ( );
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // Lê o primeiro campo presente no mapa do n8n (só chaves informadas)
    private String texto ( Map < String, Object > mapa, String... chaves )
    {
        if ( mapa == null )
        {
            return null;
        }

        for ( String chave : chaves )
        {
            Object valor = mapa.get ( chave );
            if ( valor != null && !valor.toString ( ).isBlank ( ) )
            {
                return valor.toString ( ).trim ( );
            }
        }

        return null;
    }

    // Token de uso único do .bat (24 chars aleatórios) — não dá para adivinhar
    private String gerarTokenFerramenta ( )
    {
        return shortener.generateTokenSeguro ( );
    }

    // Senha temporária (SecureRandom) gerada pelo Nump no consentimento.
    // É aplicada na máquina do cliente pelo script — dá o acesso não atendido.
    private String gerarSenhaTemporaria ( )
    {
        // sem 0/O e 1/I para evitar confusão ao digitar
        String alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

        StringBuilder senha  = new StringBuilder ( );
        SecureRandom random  = new SecureRandom ( );

        for ( int i = 0; i < 12; i++ )
        {
            senha.append ( alfabeto.charAt ( random.nextInt ( alfabeto.length ( ) ) ) );
        }

        return senha.toString ( );
    }

    // Mantém só caracteres seguros para entrar dentro do .bat (evita injeção).
    // Permite "/" e ":" para a URL de download do RustDesk ficar íntegra.
    private String sanitizar ( String valor )
    {
        if ( valor == null )
        {
            return null;
        }
        String limpo = valor.replaceAll ( "[^A-Za-z0-9.:_/-]", "" );
        return limpo.isBlank ( ) ? null : limpo;
    }

    private String getCurrentUserEmail ( )
    {
        Authentication auth = SecurityContextHolder.getContext ( ).getAuthentication ( );
        return auth == null ? "" : auth.getName ( );
    }
}
