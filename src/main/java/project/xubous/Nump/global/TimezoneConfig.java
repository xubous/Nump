package project.xubous.Nump.global;

import java.util.TimeZone;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

// Fixa o fuso horário da aplicação ANTES de qualquer LocalDateTime.now ( ).
// Sem isso, o container (UTC) grava os horários 3h adiantados em relação ao
// Brasil — ex.: "criado 17:41" quando eram 14:41. O fuso vem de app.timezone
// (env APP_TIMEZONE), com padrão America/Sao_Paulo.
@Configuration
public class TimezoneConfig
{
    @Value ( "${app.timezone:America/Sao_Paulo}" )
    private String timezone;

    @PostConstruct
    public void init ( )
    {
        TimeZone.setDefault ( TimeZone.getTimeZone ( timezone ) );
    }
}
