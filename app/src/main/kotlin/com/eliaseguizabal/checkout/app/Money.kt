package com.eliaseguizabal.checkout.app

import java.util.Locale

fun formatCents(cents: Long): String =
    String.format(Locale.US, "$%d.%02d", cents / 100, cents % 100)
