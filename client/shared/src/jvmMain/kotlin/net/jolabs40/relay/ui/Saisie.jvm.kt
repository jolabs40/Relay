package net.jolabs40.relay.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FORMAT = DateTimeFormatter.ofPattern("dd/MM HH:mm")
private val FORMAT_HEURE = DateTimeFormatter.ofPattern("HH:mm")

actual fun dateCourte(millis: Long): String =
    FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

actual fun heure(millis: Long): String {
    val moment = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return (if (moment.toLocalDate() == LocalDate.now()) FORMAT_HEURE else FORMAT).format(moment)
}
