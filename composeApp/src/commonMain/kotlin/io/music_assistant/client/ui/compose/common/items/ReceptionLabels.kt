package io.music_assistant.client.ui.compose.common.items

import io.music_assistant.client.data.model.client.AccoladeKind
import io.music_assistant.client.data.model.client.DrQuality
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.filter_label_rfu
import musicassistantclient.composeapp.generated.resources.filter_label_sitf
import musicassistantclient.composeapp.generated.resources.filter_label_tymhm
import musicassistantclient.composeapp.generated.resources.filter_label_ymio
import musicassistantclient.composeapp.generated.resources.reception_accolade_aoty
import musicassistantclient.composeapp.generated.resources.reception_accolade_honorable_mention
import musicassistantclient.composeapp.generated.resources.reception_accolade_lit
import musicassistantclient.composeapp.generated.resources.reception_accolade_record_of_the_month
import musicassistantclient.composeapp.generated.resources.reception_accolade_review
import musicassistantclient.composeapp.generated.resources.reception_accolade_rfu
import musicassistantclient.composeapp.generated.resources.reception_accolade_score_revised
import musicassistantclient.composeapp.generated.resources.reception_accolade_sitf
import musicassistantclient.composeapp.generated.resources.reception_accolade_tymhm
import musicassistantclient.composeapp.generated.resources.reception_accolade_ymio
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

/**
 * The accolade's full display name, shared by the reception panel's chips and the filter
 * sheet's award chips so the two surfaces can't drift. Null for [AccoladeKind.UNKNOWN],
 * which has no localized name — callers fall back to the raw display string.
 */
fun AccoladeKind.labelRes(): StringResource? = when (this) {
    AccoladeKind.AOTY -> Res.string.reception_accolade_aoty
    AccoladeKind.RECORD_OF_THE_MONTH -> Res.string.reception_accolade_record_of_the_month
    AccoladeKind.HONORABLE_MENTION -> Res.string.reception_accolade_honorable_mention
    AccoladeKind.SCORE_REVISED -> Res.string.reception_accolade_score_revised
    AccoladeKind.REVIEW -> Res.string.reception_accolade_review
    AccoladeKind.TYMHM -> Res.string.reception_accolade_tymhm
    AccoladeKind.SITF -> Res.string.reception_accolade_sitf
    AccoladeKind.YMIO -> Res.string.reception_accolade_ymio
    AccoladeKind.LIT -> Res.string.reception_accolade_lit
    AccoladeKind.RFU -> Res.string.reception_accolade_rfu
    AccoladeKind.UNKNOWN -> null
}

/**
 * The chip-sized accolade label: the deliberately abbreviated form where a surface has
 * one ("TYMHM" rather than "Things You Might Have Missed"), otherwise the full
 * [labelRes] name. Null for [AccoladeKind.UNKNOWN], same as [labelRes].
 */
fun AccoladeKind.shortLabelRes(): StringResource? = when (this) {
    AccoladeKind.TYMHM -> Res.string.filter_label_tymhm
    AccoladeKind.SITF -> Res.string.filter_label_sitf
    AccoladeKind.YMIO -> Res.string.filter_label_ymio
    AccoladeKind.RFU -> Res.string.filter_label_rfu
    else -> labelRes()
}
