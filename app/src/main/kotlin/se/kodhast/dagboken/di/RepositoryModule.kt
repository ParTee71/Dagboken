package se.kodhast.dagboken.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import se.kodhast.dagboken.data.medicines.AssetMedicineRepository
import se.kodhast.dagboken.data.medicines.MedicineRepository
import se.kodhast.dagboken.data.repository.ActivityRepository
import se.kodhast.dagboken.data.repository.DefaultActivityRepository
import se.kodhast.dagboken.data.repository.DefaultDoseRepository
import se.kodhast.dagboken.data.repository.DefaultEventRepository
import se.kodhast.dagboken.data.repository.DefaultIllnessRepository
import se.kodhast.dagboken.data.repository.DefaultOptionsRepository
import se.kodhast.dagboken.data.repository.DefaultPrescriptionRepository
import se.kodhast.dagboken.data.repository.DefaultPrnMedicineRepository
import se.kodhast.dagboken.data.repository.DefaultScreeningRepository
import se.kodhast.dagboken.data.repository.DefaultSettingsRepository
import se.kodhast.dagboken.data.repository.DoseRepository
import se.kodhast.dagboken.data.repository.EventRepository
import se.kodhast.dagboken.data.repository.IllnessRepository
import se.kodhast.dagboken.data.repository.OptionsRepository
import se.kodhast.dagboken.data.repository.PrescriptionRepository
import se.kodhast.dagboken.data.repository.PrnMedicineRepository
import se.kodhast.dagboken.data.repository.ScreeningRepository
import se.kodhast.dagboken.data.repository.SettingsRepository

/** Repositories – tunna fasader över samlingarna (skill firestore-data-layer). */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun settings(repository: DefaultSettingsRepository): SettingsRepository

    @Binds abstract fun options(repository: DefaultOptionsRepository): OptionsRepository

    @Binds abstract fun prescriptions(repository: DefaultPrescriptionRepository): PrescriptionRepository

    @Binds abstract fun medicineList(repository: AssetMedicineRepository): MedicineRepository

    @Binds abstract fun prnMedicines(repository: DefaultPrnMedicineRepository): PrnMedicineRepository

    @Binds abstract fun doses(repository: DefaultDoseRepository): DoseRepository

    @Binds abstract fun screenings(repository: DefaultScreeningRepository): ScreeningRepository

    @Binds abstract fun illnesses(repository: DefaultIllnessRepository): IllnessRepository

    @Binds abstract fun activities(repository: DefaultActivityRepository): ActivityRepository

    @Binds abstract fun events(repository: DefaultEventRepository): EventRepository
}
