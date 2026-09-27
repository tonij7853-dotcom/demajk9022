package chat.stoat.api.settings

object NotificationSettingsProvider {
    fun isChannelMuted(channelId: String, serverId: String?): Boolean {
        if (SyncedSettings.notifications.server.isEmpty() && SyncedSettings.notifications.channel.isEmpty()) {
            runCatching {
                chat.stoat.StoatApplication.instance.let { SyncedSettings.initFromStorage(it) }
            }
        }

        if (serverId != null) {
            // When the server is muted, all channels are muted
            if (SyncedSettings.notifications.server[serverId] == "muted") return true
        }

        return SyncedSettings.notifications.channel[channelId] == "muted"
    }
}