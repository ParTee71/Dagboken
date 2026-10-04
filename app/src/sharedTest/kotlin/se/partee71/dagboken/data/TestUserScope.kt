package se.partee71.dagboken.data

import kotlinx.coroutines.flow.MutableStateFlow
import se.partee71.dagboken.core.schema.Schema
import se.partee71.dagboken.data.common.UserScope
import se.partee71.dagboken.data.common.UserVersion

/** En inloggad användare för tester, med styrbar version. */
class TestUserScope(
    uid: String? = "uid-test",
    version: Int? = Schema.CURRENT_VERSION,
) : UserScope {
    override val uid = MutableStateFlow(uid)
    override val schemaVersion = MutableStateFlow(versionOf(uid, version))
    override val unreadable = MutableStateFlow<String?>(null)

    /** Sätter användarens version; `null` = okänd. */
    fun setVersion(version: Int?) {
        schemaVersion.value = versionOf(uid.value, version)
    }

    private fun versionOf(id: String?, version: Int?) =
        if (id != null && version != null) UserVersion(id, version) else null
}
