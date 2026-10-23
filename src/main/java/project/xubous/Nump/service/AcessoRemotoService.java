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

    // URL pública do Nump (o .bat baixa o script PowerShell por ela)
    @Value ( "${app.base-url:http://localhost:8081}" )
    private String appBaseUrl;

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
                        // Avisa o n8n ANTES de apagar (best-effort, não bloqueia):
                        // o n8n pode usar isto para fechar/apagar o chamado dele.
                        Map < String, Object > payload = new HashMap < > ( );
                        payload.put ( "id", acesso.getId ( ) );
                        payload.put ( "token", acesso.getToken ( ) );
                        payload.put ( "clienteEmail", acesso.getClienteEmail ( ) );
                        payload.put ( "status", String.valueOf ( acesso.getStatus ( ) ) );
                        payload.put ( "rustdeskId", acesso.getRustdeskId ( ) );
                        webhookPublisher.publicar ( "acesso_removido", payload );

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

    // Gera o .bat 100% em CMD (sem PowerShell). Baixa o RustDesk, instala em silêncio,
    // aplica serviço + senha para acesso NÃO ATENDIDO, pega o ID e envia ao Nump.
    public String gerarFerramenta ( String token )
    {
        AcessoRemoto acesso = acessoRemotoRepository.findByToken ( token )
                .orElseThrow ( ( ) -> new IllegalArgumentException ( "Link de acesso inválido" ) );

        if ( acesso.getStatus ( ) != StatusAcessoRemoto.CONSENTIDO )
        {
            throw new LinkIndisponivelException ( "Autorize o acesso antes de baixar a ferramenta" );
        }

        String baseUrl = appBaseUrl;
        String url     = sanitizar ( rustdeskDownloadUrl );
        String senha   = sanitizar ( acesso.getRustdeskSenha ( ) );
        String srv     = sanitizar ( acesso.getRustdeskServer ( ) );

        StringBuilder bat = new StringBuilder ( );
        bat.append ( "@echo off\r\n" );
        bat.append ( "setlocal enabledelayedexpansion\r\n" );
        bat.append ( "title Atendimento remoto - Nump\r\n" );
        bat.append ( "chcp 65001 >nul\r\n" );
        // Tenta remover Mark of the Web se existir
        bat.append ( "powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -Command \"try{ Unblock-File -Path '%~f0' -ErrorAction SilentlyContinue }catch{}; try{ Remove-Item -Path '%~f0:Zone.Identifier' -Force -ErrorAction SilentlyContinue }catch{}\" >nul 2>&1\r\n" );
        bat.append ( "\r\n" );
        // Elevação: se não for admin, pede UAC via VBS/Start-Process equivalente em cmd
        bat.append ( "net session >nul 2>&1\r\n" );
        bat.append ( "if %errorlevel% neq 0 (\r\n" );
        bat.append ( "  echo O Windows vai pedir permissao de administrador...\r\n" );
        bat.append ( "  powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -Command \"Start-Process -FilePath '%~f0' -Verb RunAs\" >nul 2>&1\r\n" );
        bat.append ( "  if errorlevel 1 mshta vbscript:CreateObject(\"Shell.Application\").ShellExecute(\"%~f0\",\"\",\"\",\"runas\",1)(window.close)\r\n" );
        bat.append ( "  exit /b\r\n" );
        bat.append ( ")\r\n" );
        bat.append ( "\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  ============================================\r\n" );
        bat.append ( "echo   Atendimento remoto seguro - Nump\r\n" );
        bat.append ( "echo  ============================================\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo  Preparando acesso remoto, aguarde...\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "\r\n" );
        bat.append ( "set \"INST=%TEMP%\\nump_rustdesk.exe\"\r\n" );
        bat.append ( "set \"APP=\"\r\n" );
        bat.append ( "if exist \"C:\\Program Files\\RustDesk\\rustdesk.exe\" set \"APP=C:\\Program Files\\RustDesk\\rustdesk.exe\"\r\n" );
        bat.append ( "if not defined APP if exist \"%ProgramFiles(x86)%\\RustDesk\\rustdesk.exe\" set \"APP=%ProgramFiles(x86)%\\RustDesk\\rustdesk.exe\"\r\n" );
        bat.append ( "if not defined APP if exist \"%LOCALAPPDATA%\\Programs\\RustDesk\\rustdesk.exe\" set \"APP=%LOCALAPPDATA%\\Programs\\RustDesk\\rustdesk.exe\"\r\n" );
        bat.append ( "\r\n" );
        // Se já tiver instalado, pula download
        bat.append ( "if defined APP (\r\n" );
        bat.append ( "  echo RustDesk ja instalado. Pulando download...\r\n" );
        bat.append ( ") else (\r\n" );
        bat.append ( "  echo Baixando RustDesk...\r\n" );
        bat.append ( "  if exist \"%INST%\" del /f /q \"%INST%\" >nul 2>&1\r\n" );
        bat.append ( "  curl -L --silent --show-error -o \"%INST%\" \"" + url + "\"\r\n" );
        bat.append ( "  if not exist \"%INST%\" bitsadmin /transfer numpdw /download /priority normal \"" + url + "\" \"%INST%\" >nul 2>&1\r\n" );
        bat.append ( "  if not exist \"%INST%\" certutil -urlcache -split -f \"" + url + "\" \"%INST%\" >nul 2>&1\r\n" );
        bat.append ( "  if not exist \"%INST%\" powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -Command \"[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '" + url + "' -OutFile '%INST%' -UseBasicParsing\" >nul 2>&1\r\n" );
        bat.append ( "  if not exist \"%INST%\" (\r\n" );
        bat.append ( "    echo.\r\n" );
        bat.append ( "    echo Nao foi possivel baixar o instalador.\r\n" );
        bat.append ( "    echo Baixe manualmente e informe o codigo ao tecnico.\r\n" );
        bat.append ( "    pause\r\n" );
        bat.append ( "    exit /b 1\r\n" );
        bat.append ( "  )\r\n" );
        bat.append ( "  echo Instalando RustDesk (silencioso)...\r\n" );
        bat.append ( "  start /wait \"\" \"%INST%\" --silent-install\r\n" );
        bat.append ( "  timeout /t 12 /nobreak >nul\r\n" );
        bat.append ( "  if exist \"%INST%\" del /f /q \"%INST%\" >nul 2>&1\r\n" );
        bat.append ( "  if exist \"C:\\Program Files\\RustDesk\\rustdesk.exe\" set \"APP=C:\\Program Files\\RustDesk\\rustdesk.exe\"\r\n" );
        bat.append ( "  if not defined APP if exist \"%ProgramFiles(x86)%\\RustDesk\\rustdesk.exe\" set \"APP=%ProgramFiles(x86)%\\RustDesk\\rustdesk.exe\"\r\n" );
        bat.append ( "  if not defined APP if exist \"%LOCALAPPDATA%\\Programs\\RustDesk\\rustdesk.exe\" set \"APP=%LOCALAPPDATA%\\Programs\\RustDesk\\rustdesk.exe\"\r\n" );
        bat.append ( ")\r\n" );
        bat.append ( "\r\n" );
        bat.append ( "if not defined APP (\r\n" );
        bat.append ( "  echo RustDesk nao encontrado.\r\n" );
        bat.append ( "  pause\r\n" );
        bat.append ( "  exit /b 1\r\n" );
        bat.append ( ")\r\n" );
        bat.append ( "\r\n" );
        if ( srv != null && !srv.isBlank() )
        {
            bat.append ( "set \"RUSTDESK_API_HOST=" + srv + "\"\r\n" );
        }
        bat.append ( "echo Configurando acesso nao atendido...\r\n" );
        bat.append ( "taskkill /f /im rustdesk.exe >nul 2>&1\r\n" );
        bat.append ( "taskkill /f /im rustdesksvc.exe >nul 2>&1\r\n" );
        bat.append ( "timeout /t 3 /nobreak >nul\r\n" );
        bat.append ( "start /wait \"\" \"%APP%\" --install-service >nul 2>&1\r\n" );
        bat.append ( "timeout /t 5 /nobreak >nul\r\n" );
        bat.append ( "start /wait \"\" \"%APP%\" --password \"" + senha + "\" >nul 2>&1\r\n" );
        bat.append ( "timeout /t 3 /nobreak >nul\r\n" );
        bat.append ( "net stop RustDesk >nul 2>&1\r\n" );
        bat.append ( "net start RustDesk >nul 2>&1\r\n" );
        bat.append ( "timeout /t 6 /nobreak >nul\r\n" );
        bat.append ( "\r\n" );
        bat.append ( "echo Abrindo RustDesk...\r\n" );
        bat.append ( "start \"\" \"%APP%\"\r\n" );
        bat.append ( "timeout /t 10 /nobreak >nul\r\n" );
        bat.append ( "\r\n" );
        bat.append ( "set \"ID=\"\r\n" );
        bat.append ( "for /L %%i in (1,1,12) do (\r\n" );
        bat.append ( "  for /f \"tokens=*\" %%a in ('\"%APP%\" --get-id 2^>nul') do (\r\n" );
        bat.append ( "    echo %%a | findstr /R \"[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]\" >nul && set \"ID=%%a\"\r\n" );
        bat.append ( "  )\r\n" );
        bat.append ( "  if defined ID goto temid\r\n" );
        bat.append ( "  timeout /t 3 /nobreak >nul\r\n" );
        bat.append ( ")\r\n" );
        bat.append ( ":temid\r\n" );
        bat.append ( "\r\n" );
        bat.append ( "if defined ID (\r\n" );
        bat.append ( "  echo.\r\n" );
        bat.append ( "  echo  ============================================\r\n" );
        bat.append ( "  echo   CODIGO DE ACESSO: !ID!\r\n" );
        bat.append ( "  echo  ============================================\r\n" );
        bat.append ( "  echo.\r\n" );
        bat.append ( "  echo %ID% | clip\r\n" );
        bat.append ( "  echo Codigo copiado para a area de transferencia.\r\n" );
        bat.append ( "  curl -s -X POST \"" + baseUrl + "/acessos/ferramenta/" + token + "/id\" -H \"Content-Type: application/json\" -d \"{\\\"rustdeskId\\\":\\\"!ID!\\\"}\" >nul 2>&1\r\n" );
        bat.append ( ") else (\r\n" );
        bat.append ( "  echo.\r\n" );
        bat.append ( "  echo Abra o RustDesk e informe o codigo ao tecnico.\r\n" );
        bat.append ( ")\r\n" );
        bat.append ( "\r\n" );
        bat.append ( "echo.\r\n" );
        bat.append ( "echo A SENHA ja esta aplicada. Tecnico pode conectar pelo ID.\r\n" );
        bat.append ( "echo Mantenha o RustDesk aberto.\r\n" );
        bat.append ( "echo Esta janela fecha em 20 segundos...\r\n" );
        bat.append ( "timeout /t 20 /nobreak >nul\r\n" );
        bat.append ( "exit\r\n" );

        return bat.toString ( );
    }

    // Gera o script PowerShell de configuração (baixado pelo .bat). Ele:
    //   - detecta o RustDesk JÁ instalado (pula download/instalação — tratamento
    //     para quem já tem o programa na máquina);
    //   - instala em silêncio só quando necessário;
    //   - aplica serviço + senha (acesso não atendido);
    //   - lê o ID, mostra na tela, copia para o clipboard e envia ao Nump.
    public String gerarScriptPowerShell ( String token )
    {
        AcessoRemoto acesso = acessoRemotoRepository.findByToken ( token )
                .orElseThrow ( ( ) -> new IllegalArgumentException ( "Link de acesso inválido" ) );

        if ( acesso.getStatus ( ) != StatusAcessoRemoto.CONSENTIDO )
        {
            throw new LinkIndisponivelException ( "Autorize o acesso antes de baixar a ferramenta" );
        }

        String url      = sanitizar ( rustdeskDownloadUrl );
        String senha    = sanitizar ( acesso.getRustdeskSenha ( ) );
        String servidor = sanitizar ( acesso.getRustdeskServer ( ) );
        String baseUrl  = appBaseUrl;

        StringBuilder ps = new StringBuilder ( );
        ps.append ( "$ErrorActionPreference = 'Continue'\r\n" );
        ps.append ( "$NumpToken = '" + token + "'\r\n" );
        ps.append ( "$NumpBase  = '" + baseUrl + "'\r\n" );
        ps.append ( "$Url       = '" + url + "'\r\n" );
        ps.append ( "$Senha     = '" + senha + "'\r\n" );
        if ( servidor != null )
        {
            ps.append ( "$Server    = '" + servidor + "'\r\n" );
        }
        ps.append ( "\r\n" );
        ps.append ( "function Titulo($t){ Write-Host ''; Write-Host ('  ' + $t) -ForegroundColor Cyan }\r\n" );
        // Roda o rustdesk.exe com argumentos e ESPERA COM TIMEOUT. Sem isso, um
        // Start-Process -Wait pode travar para sempre quando já existe uma
        // instância do RustDesk aberta (o processo novo "cola" na instância viva
        // e nunca retorna). O timeout garante que o script sempre avança.
        ps.append ( "function RodarRustDesk([string[]]$arg, [int]$segundos = 30){\r\n" );
        ps.append ( "  try{\r\n" );
        ps.append ( "    $p = Start-Process -FilePath $app -ArgumentList $arg -PassThru -WindowStyle Hidden\r\n" );
        ps.append ( "    $p | Wait-Process -Timeout $segundos -ErrorAction SilentlyContinue\r\n" );
        ps.append ( "    if(-not $p.HasExited){ try{ $p | Stop-Process -Force -ErrorAction SilentlyContinue }catch{} }\r\n" );
        ps.append ( "  }catch{}\r\n" );
        ps.append ( "}\r\n" );
        // Fecha instâncias do RustDesk em execução (do usuário e do serviço) para
        // que --install-service / --password apliquem de verdade, sem travar.
        ps.append ( "function FecharRustDesk{\r\n" );
        ps.append ( "  Get-Process rustdesk -ErrorAction SilentlyContinue | ForEach-Object {\r\n" );
        ps.append ( "    try{ $_.CloseMainWindow() | Out-Null }catch{}\r\n" );
        ps.append ( "  }\r\n" );
        ps.append ( "  Start-Sleep -Seconds 2\r\n" );
        ps.append ( "  Get-Process rustdesk -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue\r\n" );
        ps.append ( "  Start-Sleep -Seconds 2\r\n" );
        ps.append ( "}\r\n" );
        // Procura o rustdesk.exe nos caminhos possíveis (instalado por admin ou por usuário)
        ps.append ( "function AchouExe{\r\n" );
        ps.append ( "  $c = @(\r\n" );
        ps.append ( "    (Join-Path $env:ProgramFiles 'RustDesk\\rustdesk.exe'),\r\n" );
        ps.append ( "    (Join-Path ${env:ProgramFiles(x86)} 'RustDesk\\rustdesk.exe'),\r\n" );
        ps.append ( "    (Join-Path $env:LOCALAPPDATA 'RustDesk\\rustdesk.exe'),\r\n" );
        ps.append ( "    (Join-Path $env:LOCALAPPDATA 'Programs\\RustDesk\\rustdesk.exe')\r\n" );
        ps.append ( "  )\r\n" );
        ps.append ( "  foreach($p in $c){ if($p -and (Test-Path $p)){ return $p } }\r\n" );
        ps.append ( "  return $null\r\n" );
        ps.append ( "}\r\n" );
        ps.append ( "\r\n" );
        ps.append ( "Write-Host ''\r\n" );
        ps.append ( "Write-Host '  ============================================' -ForegroundColor Green\r\n" );
        ps.append ( "Write-Host '   Atendimento remoto seguro - Nump' -ForegroundColor Green\r\n" );
        ps.append ( "Write-Host '  ============================================' -ForegroundColor Green\r\n" );
        ps.append ( "Write-Host ''\r\n" );
        // ⚠️ PowerShell NÃO tem `goto` — o fluxo usa try/catch/finally.
        // `$ok` indica que a configuração terminou; o finally sempre mostra o
        // resumo e a janela NUNCA fecha em silêncio.
        ps.append ( "$ok = $false\r\n" );
        ps.append ( "$id = ''\r\n" );
        ps.append ( "try {\r\n" );
        // ── Já tem RustDesk? Então não baixa nem instala de novo ─────────────
        ps.append ( "  $app = AchouExe\r\n" );
        ps.append ( "  if($app){\r\n" );
        ps.append ( "    Titulo 'RustDesk ja esta instalado - pulando download e instalacao.'\r\n" );
        ps.append ( "  } else {\r\n" );
        ps.append ( "    $inst = Join-Path $env:TEMP 'nump_rustdesk_setup.exe'\r\n" );
        ps.append ( "    Titulo 'Baixando o RustDesk (isso demora um pouco)...'\r\n" );
        ps.append ( "    $baixou = $false\r\n" );
        ps.append ( "    if(Test-Path $inst){ $baixou = $true }\r\n" );
        ps.append ( "    if(-not $baixou){\r\n" );
        ps.append ( "      try{\r\n" );
        ps.append ( "        $ce = (Get-Command curl.exe -ErrorAction SilentlyContinue).Source\r\n" );
        ps.append ( "        if($ce){ & $ce -L --progress-bar -o $inst $Url; if(Test-Path $inst){ $baixou = $true } }\r\n" );
        ps.append ( "      }catch{}\r\n" );
        ps.append ( "    }\r\n" );
        ps.append ( "    if(-not $baixou){\r\n" );
        ps.append ( "      try{ Import-Module BitsTransfer -ErrorAction Stop; Start-BitsTransfer -Source $Url -Destination $inst }\r\n" );
        ps.append ( "      catch{ [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri $Url -OutFile $inst -UseBasicParsing }\r\n" );
        ps.append ( "      if(Test-Path $inst){ $baixou = $true }\r\n" );
        ps.append ( "    }\r\n" );
        ps.append ( "    if(-not $baixou){ throw 'nao consegui baixar o instalador do RustDesk' }\r\n" );
        ps.append ( "    Titulo 'Instalando o RustDesk (pode aparecer uma janela de progresso)...'\r\n" );
        ps.append ( "    $pi = Start-Process -FilePath $inst -ArgumentList '--silent-install' -PassThru\r\n" );
        ps.append ( "    $pi | Wait-Process -Timeout 180 -ErrorAction SilentlyContinue\r\n" );
        ps.append ( "    Start-Sleep -Seconds 12\r\n" );
        ps.append ( "    $app = AchouExe\r\n" );
        ps.append ( "  }\r\n" );
        ps.append ( "  if(-not $app){ throw 'rustdesk.exe nao encontrado' }\r\n" );
        ps.append ( "\r\n" );
        if ( servidor != null )
        {
            ps.append ( "  $env:RUSTDESK_API_HOST = $Server\r\n" );
        }
        // ── Serviço + senha (acesso não atendido) ─────────────────────────────
        // Fecha instâncias abertas antes de configurar (senão o --password trava
        // ou não grava no config do serviço).
        ps.append ( "  Titulo 'Preparando o acesso (fechando instancias abertas do RustDesk)...'\r\n" );
        ps.append ( "  FecharRustDesk\r\n" );
        ps.append ( "  Titulo 'Configurando o acesso nao atendido (servico + senha)...'\r\n" );
        ps.append ( "  RodarRustDesk @('--install-service') 60\r\n" );
        ps.append ( "  Start-Sleep -Seconds 6\r\n" );
        // Gamal: aplica a senha. RodarRustDesk evita o travamento de -Wait.
        ps.append ( "  RodarRustDesk @('--password', $Senha) 30\r\n" );
        ps.append ( "  Start-Sleep -Seconds 4\r\n" );
        // Reinicia o serviço para a senha valer de fato no processo do serviço.
        ps.append ( "  try{ Restart-Service RustDesk -Force -ErrorAction SilentlyContinue; Start-Sleep -Seconds 6 }catch{}\r\n" );
        ps.append ( "\r\n" );
        // ── Abre o RustDesk e lê o ID ────────────────────────────────────────
        ps.append ( "  Titulo 'Abrindo o RustDesk...'\r\n" );
        ps.append ( "  Start-Process -FilePath $app -ErrorAction SilentlyContinue\r\n" );
        ps.append ( "  Titulo 'Aguardando o codigo de acesso do RustDesk...'\r\n" );
        ps.append ( "  for($i = 0; $i -lt 20 -and -not $id; $i++){\r\n" );
        ps.append ( "    Start-Sleep -Seconds 3\r\n" );
        ps.append ( "    try{\r\n" );
        ps.append ( "      $out = & $app --get-id 2>$null | Out-String\r\n" );
        ps.append ( "      if($out -match '(\\d{6,})'){ $id = $Matches[1] }\r\n" );
        ps.append ( "    }catch{}\r\n" );
        ps.append ( "  }\r\n" );
        ps.append ( "\r\n" );
        // ── Envia o ID ao Nump (o técnico copia no painel, sem digitação) ────
        ps.append ( "  if($id){\r\n" );
        ps.append ( "    try{\r\n" );
        ps.append ( "      [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12\r\n" );
        ps.append ( "      $body = @{ rustdeskId = $id } | ConvertTo-Json -Compress\r\n" );
        ps.append ( "      Invoke-WebRequest -Uri ($NumpBase + '/acessos/ferramenta/' + $NumpToken + '/id') -Method Post -ContentType 'application/json' -Body $body -UseBasicParsing -TimeoutSec 15 | Out-Null\r\n" );
        ps.append ( "      Write-Host '  Codigo enviado ao tecnico.' -ForegroundColor Green\r\n" );
        ps.append ( "    }catch{ Write-Host '  (nao consegui avisar o tecnico, envie o codigo manualmente)' -ForegroundColor DarkGray }\r\n" );
        ps.append ( "  }\r\n" );
        ps.append ( "  $ok = $true\r\n" );
        ps.append ( "} catch {\r\n" );
        ps.append ( "  Write-Host ''\r\n" );
        ps.append ( "  Write-Host '  Nao foi possivel preparar o acesso remoto.' -ForegroundColor Red\r\n" );
        ps.append ( "  Write-Host ('  Detalhe: ' + $_.Exception.Message) -ForegroundColor DarkGray\r\n" );
        ps.append ( "  Write-Host '  Abra o RustDesk manualmente e informe o codigo ao tecnico.' -ForegroundColor Red\r\n" );
        ps.append ( "} finally {\r\n" );
        ps.append ( "  Write-Host ''\r\n" );
        ps.append ( "  Write-Host '  --------------------------------------------' -ForegroundColor Yellow\r\n" );
        ps.append ( "  if($id){\r\n" );
        ps.append ( "    Write-Host ('   CODIGO DE ACESSO: ' + $id) -ForegroundColor Yellow\r\n" );
        ps.append ( "  } else {\r\n" );
        ps.append ( "    Write-Host '   Abra o RustDesk na janela que apareceu e leia o ID.' -ForegroundColor Yellow\r\n" );
        ps.append ( "  }\r\n" );
        ps.append ( "  Write-Host '  --------------------------------------------' -ForegroundColor Yellow\r\n" );
        ps.append ( "  Write-Host ''\r\n" );
        ps.append ( "  if($ok){\r\n" );
        ps.append ( "    Write-Host '  A SENHA de atendimento ja esta aplicada.' -ForegroundColor White\r\n" );
        ps.append ( "    Write-Host '  O tecnico ja recebeu este codigo - nao precisa enviar.' -ForegroundColor White\r\n" );
        ps.append ( "  }\r\n" );
        ps.append ( "  if($id){ try{ Set-Clipboard -Value $id; Write-Host '  (codigo copiado para a area de transferencia)' -ForegroundColor DarkGray }catch{} }\r\n" );
        ps.append ( "  Write-Host '  Mantenha o RustDesk aberto ate o atendimento acabar.' -ForegroundColor White\r\n" );
        ps.append ( "  Write-Host '  Ao final, use os botoes da pagina para encerrar ou desinstalar.' -ForegroundColor White\r\n" );
        ps.append ( "  Write-Host ''\r\n" );
        ps.append ( "  Write-Host '  Esta janela fecha sozinha em 15 segundos...' -ForegroundColor DarkGray\r\n" );
        ps.append ( "  Start-Sleep -Seconds 15\r\n" );
        ps.append ( "}\r\n" );

        return ps.toString ( );
    }

    // O script envia o ID do RustDesk quando descobre — o painel do técnico passa
    // a mostrar o "código de acesso" sem o cliente precisar digitar nada.
    // O ID descoberto na máquina é AUTORITATIVO: sobrescreve um ID vindo do n8n
    // (que pode estar desatualizado), garantindo que o painel mostre o valor real.
    @Transactional
    public void registrarIdPelaFerramenta ( String token, String rustdeskId )
    {
        if ( rustdeskId == null || rustdeskId.isBlank ( ) )
        {
            return;
        }

        String idLimpo = rustdeskId.trim ( );

        acessoRemotoRepository.findByToken ( token ).ifPresent ( ( acesso ) ->
        {
            if ( acesso.getStatus ( ) == StatusAcessoRemoto.CONSENTIDO
                    && !idLimpo.equals ( acesso.getRustdeskId ( ) ) )
            {
                acesso.setRustdeskId ( idLimpo );
                // marca que a ferramenta já rodou na máquina (auditoria)
                if ( acesso.getFerramentaUsadaEm ( ) == null )
                {
                    acesso.setFerramentaUsadaEm ( java.time.LocalDateTime.now ( ) );
                }
                acessoRemotoRepository.save ( acesso );
            }
        } );
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

    // Cliente finaliza o acesso pela própria página — igual estilo "fechar sessão" do AnyDesk
    @Transactional
    public void finalizarPeloCliente ( String token )
    {
        acessoRemotoRepository.findByToken ( token )
                .ifPresent ( ( acesso ) ->
                {
                    if ( acesso.getStatus ( ) == StatusAcessoRemoto.CONSENTIDO )
                    {
                        acesso.setStatus ( StatusAcessoRemoto.ENCERRADO );
                        acessoRemotoRepository.save ( acesso );
                    }
                } );
    }

    private String getCurrentUserEmail ( )
    {
        Authentication auth = SecurityContextHolder.getContext ( ).getAuthentication ( );
        return auth == null ? "" : auth.getName ( );
    }
}
