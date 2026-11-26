package com.appcontrol.cloud;

import java.util.List;

import com.appcontrol.model.BlockRuleJtMa;

/** De donde los motores de bloqueo toman, ademas de las reglas locales, las reglas que vienen de un grupo. */
public interface GroupRulesSourceMa {

    /** Reglas de grupo vigentes (no vencidas) de ese tipo: APP o SITE. */
    List<BlockRuleJtMa> activeJt(String kindJt);

    /** Los motores cuentan cómo están (por ejemplo "sitios" = ACTIVE o CONFLICT) para que quien controla lo sepa. */
    default void reportStatusMa(String keyMa, String valueMa) {
    }
}
