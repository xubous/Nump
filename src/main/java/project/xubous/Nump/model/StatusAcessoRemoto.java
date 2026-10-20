package project.xubous.Nump.model;

// Estado de uma sessão de acesso remoto (link de consentimento RustDesk)
public enum StatusAcessoRemoto
{
    PENDENTE,    // link gerado, aguardando o cliente autorizar
    CONSENTIDO,  // cliente autorizou — sessão liberada
    EXPIRADO,    // passou do prazo sem autorizar
    CANCELADO    // técnico cancelou antes do consentimento
}
