package com.appcontrol.dto;

import java.util.List;

/** Respuesta de GET /api/status. */
public record StatusDtoJt(String app, List<String> profiles, boolean dbUp, String dbMessage) {
}
