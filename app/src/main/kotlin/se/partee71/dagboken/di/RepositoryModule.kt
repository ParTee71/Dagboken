package se.partee71.dagboken.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import se.partee71.dagboken.data.repository.ActivityRepository
import se.partee71.dagboken.data.repository.DefaultActivityRepository
import se.partee71.dagboken.data.repository.DefaultDoseRepository
import se.partee71.dagboken.data.repository.DefaultEventRepository
import se.partee71.dagboken.data.repository.DefaultIllnessRepository
import se.partee71.dagboken.data.repository.DefaultOptionsRepository
import se.partee71.dagboken.data.repository.DefaultPrescriptionRepository
import se.partee71.dagboken.data.repository.DefaultPrnMedicineRepository
import se.partee71.dagboken.data.repository.DoseRepository
import se.partee71.dagboken.data.repository.EventRepository
import se.partee71.dagboken.data.repository.IllnessRepository
import se.partee71.dagboken.data.repository.OptionsRepository
import se.partee71.dagboken.data.repository.PrescriptionRepository
import se.partee71.dagboken.data.repository.PrnMedicineRepository
import se.partee71.dagboken.data.repository.DefaultScreeningRepository
import se.partee71.dagboken.data.repository.DefaultSettingsRepository
import se.partee71.dagboken.data.repository.ScreeningRepository
import se.partee71.dagboken.data.repository.SettingsRepository

/** Repositories – tunna fasader över samlingarna (skill firestore-data-layer). */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun settings(repository: DefaultSettingsRepository): SettingsRepository

    @Binds abstract fun options(repository: DefaultOptionsRepository): OptionsRepository

    @Binds abstract fun prescriptions(repository: DefaultPrescriptionRepository): PrescriptionRepository

    @Binds abstract fun prnMedicines(repository: DefaultPrnMedicineRepository): PrnMedicineRepository

    @Binds abstract fun doses(repository: DefaultDoseRepository): DoseRepository

    @Binds abstract fun screenings(repository: DefaultScreeningRepository): ScreeningRepository

    @Binds abstract fun illnesses(repository: DefaultIllnessRepository): IllnessRepository

    @Binds abstract fun activities(repository: DefaultActivityRepository): ActivityRepository

    @Binds abstract fun events(repository: DefaultEventRepository): EventRepository
}
