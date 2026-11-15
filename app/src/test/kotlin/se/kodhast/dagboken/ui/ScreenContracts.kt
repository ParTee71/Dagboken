package se.kodhast.dagboken.ui

import se.kodhast.dagboken.data.common.DataError
import se.kodhast.dagboken.ui.common.AppErrors
import se.kodhast.dagboken.ui.theme.DagbokenTheme
import se.kodhast.ui.testing.ContractEnvironment

/**
 * Det kontraktstesterna för ramarna (android-ui:s `ui-testing`: `runListScreenContract`, `runEditScreenContract`,
 * `runDetailScreenContract`) behöver av Dagboken: temat, felöversättningen ([AppErrors]) och ett fel där
 * "Försök igen" hjälper ([DataError.Offline]).
 */
val DagbokenContracts = ContractEnvironment(AppErrors, DataError.Offline) { DagbokenTheme(content = it) }
