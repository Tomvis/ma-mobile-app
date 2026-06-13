package io.music_assistant.client.ui.compose.common.items

import io.music_assistant.client.data.model.client.DrQuality
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.reception_dr_excellent
import musicassistantclient.composeapp.generated.resources.reception_dr_fair
import musicassistantclient.composeapp.generated.resources.reception_dr_good
import musicassistantclient.composeapp.generated.resources.reception_dr_poor
import org.jetbrains.compose.resources.StringResource

/**
 * The DR-band verdict label, shared by the reception panel's DR row and the filter
 * sheet's DR bucket chips so both surfaces stay in sync.
 */
fun DrQuality.labelRes(): StringResource = when (this) {
    DrQuality.EXCELLENT -> Res.string.reception_dr_excellent
    DrQuality.GOOD -> Res.string.reception_dr_good
    DrQuality.FAIR -> Res.string.reception_dr_fair
    DrQuality.POOR -> Res.string.reception_dr_poor
}
