package com.appcontrol.update;

/**
 * Lo que el servidor dice de la versión más nueva: su número de compilación, tamaño, huella SHA-256 del archivo y la firma
 * (RSA, en base64) con la que se comprueba que de verdad la publicó quien hace AppControl.
 */
public record UpdateInfoJtMa(String build, long size, String sha256, String signature) {
}
