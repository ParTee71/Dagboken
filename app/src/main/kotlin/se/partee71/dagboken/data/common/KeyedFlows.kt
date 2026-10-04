package se.partee71.dagboken.data.common

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Kombinerar varje värde med ett flöde som bara beror på en nyckel ur värdet – t.ex. incheckningarna
 * för episoderna i en lista. Källan följs en gång; flödet för nyckeln
 * ([other]) byts bara när nyckeln ([key]) ändras, inte vid varje ändring av källan. Efter ett byte
 * kombineras inget värde med den förra nyckelns data: nästa utsändning väntar på den nya.
 * Ett fel i källan eller i [other] avslutar flödet.
 */
fun <T, K, R, V> Flow<T>.combineByKey(key: (T) -> K, other: (K) -> Flow<R>, transform: (T, R) -> V): Flow<V> = channelFlow {
    val latest = MutableStateFlow<Latest<T>?>(null)
    var currentKey: Latest<K>? = null
    var job: Job? = null
    collect { value ->
        val next = Latest(key(value))
        if (next != currentKey) {
            // Det förra flödet avbryts innan det nya värdet syns, så att det aldrig hinner kombinera dem.
            job?.cancelAndJoin()
            currentKey = next
            latest.value = Latest(value)
            job = launch {
                combine(latest.filterNotNull(), other(next.value)) { current, data -> transform(current.value, data) }.collect { send(it) }
            }
        } else {
            latest.value = Latest(value)
        }
    }
}

/** Ett värde som kan vara `null` i ett flöde som använder `null` för "inget än". */
private data class Latest<T>(val value: T)
