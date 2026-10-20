package se.partee71.dagboken.ui

import se.partee71.dagboken.data.common.DataError
import se.partee71.dagboken.ui.common.AppErrors
import se.partee71.dagboken.ui.theme.DagbokenTheme
import se.partee71.ui.testing.ContractEnvironment

/**
 * Det kontraktstesterna för ramarna (android-ui:s `ui-testing`: `runListScreenContract`, `runEditScreenContract`,
 * `runDetailScreenContract`) behöver av Dagboken: temat, felöversättningen ([AppErrors]) och ett fel där
 * "Försök igen" hjälper ([DataError.Offline]).
 */
val DagbokenContracts = ContractEnvironment(AppErrors, DataError.Offline) { DagbokenTheme(content = it) }
