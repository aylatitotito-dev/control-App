package com.appcontrol.service;

import java.security.SecureRandom;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.appcontrol.model.UserGroupJt;
import com.appcontrol.repository.UserGroupRepositoryJt;

@Service
public class GroupServiceMa {

    /** Sin caracteres ambiguos (0/O, 1/I). */
    private static final String ALPHABET_JT = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM_JT = new SecureRandom();

    private final UserGroupRepositoryJt repoJt;

    public GroupServiceMa(UserGroupRepositoryJt repoJt) {
        this.repoJt = repoJt;
    }

    /** Codigo legible de invitacion con formato XXXX-XXXX. */
    public static String generateCodeJt() {
        StringBuilder sbJt = new StringBuilder(9);
        for (int iJt = 0; iJt < 8; iJt++) {
            if (iJt == 4) {
                sbJt.append('-');
            }
            sbJt.append(ALPHABET_JT.charAt(RANDOM_JT.nextInt(ALPHABET_JT.length())));
        }
        return sbJt.toString();
    }

    @Transactional(readOnly = true)
    public List<UserGroupJt> list() {
        return repoJt.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public UserGroupJt createJt(String nameJt) {
        String cleanJt = nameJt == null ? "" : nameJt.trim();
        if (cleanJt.isEmpty() || cleanJt.length() > 80) {
            throw new IllegalArgumentException("El nombre del grupo debe tener entre 1 y 80 caracteres");
        }
        String codeJt;
        do {
            codeJt = generateCodeJt();
        } while (repoJt.existsByCode(codeJt));
        return repoJt.save(new UserGroupJt(cleanJt, codeJt));
    }
}
