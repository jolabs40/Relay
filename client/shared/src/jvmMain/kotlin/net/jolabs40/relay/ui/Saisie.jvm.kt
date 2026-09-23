package net.jolabs40.relay.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FORMAT = DateTimeFormatter.ofPattern("dd/MM HH:mm")

actual fun dateCourte(millis: Long): String =
    FORMAT.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
