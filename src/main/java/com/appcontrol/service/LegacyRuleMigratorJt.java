package com.appcontrol.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.appcontrol.model.BlockRuleJtMa;
import com.appcontrol.model.RuleModeMa;

/**
 * Una sola vez: pasa a reglas nuevas lo que se guardo con la version anterior
 * (limites por aplicacion en "app_limit" y sitios en "blocked_site"). 0 minutos = bloqueo total.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LegacyRuleMigratorJt implements ApplicationRunner {

    private static final Logger logMa = LoggerFactory.getLogger(LegacyRuleMigratorJt.class);

    private final JdbcTemplate jdbcMa;
    private final RuleServiceJt rulesMa;

    public LegacyRuleMigratorJt(JdbcTemplate jdbcMa, RuleServiceJt rulesMa) {
        this.jdbcMa = jdbcMa;
        this.rulesMa = rulesMa;
    }

    @Override
    public void run(ApplicationArguments argsMa) {
        if (!rulesMa.listAllJtMa().isEmpty()) {
            return;
        }
        int movedMa = 0;
        try {
            List<Map<String, Object>> appsMa = jdbcMa.queryForList("SELECT app_name, daily_minutes FROM app_limit");
            for (Map<String, Object> rowMa : appsMa) {
                String nameMa = String.valueOf(rowMa.get("app_name"));
                int minutesMa = ((Number) rowMa.get("daily_minutes")).intValue();
                try {
                    rulesMa.saveJtMa(BlockRuleJtMa.APP, nameMa, nameMa,
                            minutesMa == 0 ? RuleModeMa.ALWAYS : RuleModeMa.QUOTA, BlockRuleJtMa.ALL_DAYS, 0, 0,
                            minutesMa);
                    movedMa++;
                } catch (IllegalArgumentException exMa) {
                    logMa.debug("Regla antigua omitida ({}): {}", nameMa, exMa.getMessage());
                }
            }
        } catch (Exception exMa) {
            logMa.debug("Sin limites antiguos que migrar: {}", exMa.toString());
        }
        try {
            List<Map<String, Object>> sitesMa = jdbcMa.queryForList("SELECT host FROM blocked_site");
            for (Map<String, Object> rowMa : sitesMa) {
                String hostMa = String.valueOf(rowMa.get("host"));
                try {
                    rulesMa.saveJtMa(BlockRuleJtMa.SITE, hostMa, hostMa, RuleModeMa.ALWAYS, BlockRuleJtMa.ALL_DAYS,
                            0, 0, 0);
                    movedMa++;
                } catch (IllegalArgumentException exMa) {
                    logMa.debug("Sitio antiguo omitido ({}): {}", hostMa, exMa.getMessage());
                }
            }
        } catch (Exception exMa) {
            logMa.debug("Sin sitios antiguos que migrar: {}", exMa.toString());
        }
        if (movedMa > 0) {
            logMa.info("Reglas migradas desde la version anterior: {}", movedMa);
        }
    }
}
