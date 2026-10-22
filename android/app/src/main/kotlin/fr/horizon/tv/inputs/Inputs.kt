package fr.horizon.tv.inputs

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager

data class TvInput(val id: String, val label: String, val type: Int, val connected: Boolean)

object Inputs {
    fun list(ctx: Context): List<TvInput> = runCatching {
        val tim = ctx.getSystemService(Context.TV_INPUT_SERVICE) as TvInputManager
        tim.tvInputList
            .filter { it.isPassthroughInput && !it.isHidden(ctx) }
            .map { info ->
                TvInput(
                    id = info.id,
                    label = (info.loadCustomLabel(ctx) ?: info.loadLabel(ctx)).toString(),
                    type = info.type,
                    connected = tim.getInputState(info.id) == TvInputManager.INPUT_STATE_CONNECTED,
                )
            }
            .sortedWith(compareBy({ it.type }, { it.label }))
    }.getOrDefault(emptyList())

    fun isHdmi(i: TvInput) = i.type == TvInputInfo.TYPE_HDMI

    /** Ouvre l'entrée via l'application TV du constructeur (même mécanique que le sélecteur de sources). */
    fun open(ctx: Context, input: TvInput): Boolean = runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
}
