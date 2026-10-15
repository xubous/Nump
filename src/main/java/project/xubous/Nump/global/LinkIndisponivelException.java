package project.xubous.Nump.global;

// Link público indisponível: expirado, revogado ou com limite de downloads atingido.
// Responde 410 Gone (melhor que 404: o recurso existiu, mas não está mais disponível).
public class LinkIndisponivelException extends RuntimeException
{
    public LinkIndisponivelException ( String message )
    {
        super ( message );
    }
}
