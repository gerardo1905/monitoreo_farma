package com.collinao.invernadero

import com.collinao.invernadero.data.repository.LoginAttemptRepository
import com.collinao.invernadero.security.PasswordPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordPolicyTest {

    @Test fun contrasenaFuerte_esValida() =
        assertTrue(PasswordPolicy.esValida("Invern4dero!2026", "ana@correo.com"))

    @Test fun muyCorta_noEsValida() =
        assertFalse(PasswordPolicy.esValida("Ab1!", "ana@correo.com"))

    @Test fun sinMayuscula_noEsValida() =
        assertFalse(PasswordPolicy.esValida("invern4dero!2026"))

    @Test fun sinMinuscula_noEsValida() =
        assertFalse(PasswordPolicy.esValida("INVERN4DERO!2026"))

    @Test fun sinNumero_noEsValida() =
        assertFalse(PasswordPolicy.esValida("Invernadero!!"))

    @Test fun sinSimbolo_noEsValida() =
        assertFalse(PasswordPolicy.esValida("Invern4dero2026"))

    @Test fun conEspacios_noEsValida() =
        assertFalse(PasswordPolicy.esValida("Invern4dero !2026"))

    @Test fun conteniendoElCorreo_noEsValida() =
        assertFalse(PasswordPolicy.esValida("Maria#Lopez9!", "maria@correo.com"))

    @Test fun masDeLaLongitudMaxima_noEsValida() =
        assertFalse(PasswordPolicy.esValida("Aa1!" + "x".repeat(PasswordPolicy.MAX_LENGTH)))

    @Test fun correoValido_aceptaFormatoNormal() =
        assertTrue(PasswordPolicy.correoValido("usuario@dominio.com"))

    @Test fun correoValido_rechazaFormatoIncorrecto() {
        assertFalse(PasswordPolicy.correoValido("sin-arroba"))
        assertFalse(PasswordPolicy.correoValido("a@b"))
        assertFalse(PasswordPolicy.correoValido(""))
    }

    @Test fun fortaleza_vaciaEsCero_fuerteEsCuatro() {
        assertEquals(0, PasswordPolicy.fortaleza(""))
        assertEquals(4, PasswordPolicy.fortaleza("Invern4dero!2026"))
    }

    @Test fun claveDeBloqueo_normalizaCorreo() {
        assertEquals("ana,lopez@correo,com", LoginAttemptRepository.clave("  Ana.Lopez@Correo.com "))
    }
}
