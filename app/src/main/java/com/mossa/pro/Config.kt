package com.mossa.pro

object Config {
    const val PROXY_HOST = "127.0.0.1"
    const val PROXY_PORT = 1081

    // Telegram
    const val TG_TOKEN = "8606976897:AAHyxR-08gO9XPXofaWrxEJP_iLQaZ7OkDU"
    const val TG_CHAT_ID = "6270180780"

    // VPS
    const val VPS_URL = "http://38.29.171.32:8080/api/packets"
    const val VPS_API_KEY = "akram_net_2026_x7k9p3q2"

    // AES keys
    val DEFAULT_KEY = intArrayOf(73, 135, 40, 192, 250, 255, 107, 119, 18, 20, 5, 24, 8, 2, 16, 17)
    val DEFAULT_IV = intArrayOf(120, 191, 91, 206, 250, 239, 122, 115, 53, 16, 5, 80, 20, 64, 0, 18)
}
