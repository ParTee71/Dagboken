package se.kodhast.dagboken.data

import se.kodhast.dagboken.core.model.Identified
import se.kodhast.dagboken.core.schema.Doc
import se.kodhast.dagboken.core.schema.DocCodec
import se.kodhast.dagboken.core.schema.Schema

/** `CollectionContract` mot [FakeCollection] – körs i JVM i varje PR som rör appen. */
class FakeCollectionContractTest : CollectionContract() {
    override fun createEnvironment(): Environment = object : Environment {
        private val scope = TestUserScope()
        private val clock = FixedClock()
        private val factory = FakeCollectionFactory(scope = scope, clock = clock)

        override val uid = scope.uid.value!!
        override val now = clock.instant

        override fun <T : Identified> collection(codec: DocCodec<T>, name: String, path: (uid: String?) -> String) = factory.collection(codec, name, path)

        override suspend fun writeRaw(path: String, id: String, doc: Doc) = factory.store.set(path, id, doc, merge = false)

        override suspend fun readRaw(path: String, id: String) = factory.store.read(path, id)

        override fun makeUserNewerThanApp() = scope.setVersion(Schema.CURRENT_VERSION + 1)

        override fun signOut() {
            scope.uid.value = null
        }

        override fun signInAgain() {
            scope.uid.value = uid
        }
    }
}
