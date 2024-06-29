package com.sethhaskellcondie.thegamepensiveapi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.sethhaskellcondie.thegamepensiveapi.domain.Keychain;
import com.sethhaskellcondie.thegamepensiveapi.domain.backupimport.BackupDataDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.backupimport.BackupImportGateway;
import com.sethhaskellcondie.thegamepensiveapi.domain.backupimport.ImportResultsDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomField;
import com.sethhaskellcondie.thegamepensiveapi.domain.customfield.CustomFieldValue;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.system.SystemRequestDto;
import com.sethhaskellcondie.thegamepensiveapi.domain.entity.toy.ToyRequestDto;

//@Component
public class CommandLineRunnerTemplate implements CommandLineRunner {
	@Override
	public void run(String... args) throws Exception {
		testImportBackupData_HappyPath_InitialDataCreated();
	}

//	@Autowired
	private BackupImportGateway gateway;

	//ids for custom fields and custom field values are ignored.
	private final CustomField initialCustomField = new CustomField(0, "Initial Custom Field", CustomField.TYPE_TEXT, Keychain.SYSTEM_KEY);
	private final ToyRequestDto initialToy = new ToyRequestDto("Initial Toy", "Initial Set", new ArrayList<>());

	private final CustomField toyCustomField = new CustomField(11, "Count", CustomField.TYPE_NUMBER, Keychain.TOY_KEY);
	private final ToyRequestDto secondToy = new ToyRequestDto("Second Toy", "Second Set", List.of(new CustomFieldValue(11, "Count", CustomField.TYPE_NUMBER, "4")));

	private final SystemRequestDto initialSystem = new SystemRequestDto("Initial System", 3, false, List.of(new CustomFieldValue(0, "Initial Custom Field", CustomField.TYPE_TEXT, "Initial Value")));
	private final CustomField systemCustomField = new CustomField(99, "Owned", CustomField.TYPE_BOOLEAN, Keychain.SYSTEM_KEY);
	private final SystemRequestDto secondSystem = new SystemRequestDto("Second System", 4, false, List.of(new CustomFieldValue(0, "Owned", CustomField.TYPE_BOOLEAN, "false")));

	void testImportBackupData_HappyPath_InitialDataCreated() {
		final BackupDataDto backupData = new BackupDataDto(
			List.of(initialCustomField),
			List.of(initialToy),
			List.of()
		);

		final ImportResultsDto results = gateway.importBackupData(backupData);

		validateBackupData(backupData, gateway.getBackupData());

		//call the rest of the tests in order
		testImportCustomFields_MismatchedType_ReturnErrors();
		testImportCustomFields_ValidFieldsAndInvalidTypeAndKey_ReturnSuccessAndErrors();
		testImportToys_MissingCustomField_ReturnSuccessAndErrors();
		testImportSystems_MissingCustomField_ReturnSuccessAndErrors();
		testImportSystems_SomeExistingSomeNew_ReturnSuccess();
		importBackupData_UsingRetrievedBackupData_NoNewDataImported();
	}

	void testImportCustomFields_MismatchedType_ReturnErrors() {
		final BackupDataDto backupData = new BackupDataDto(
			//The name and key match the initialCustomField but the type is a mismatch causing an error
			List.of(new CustomField(0, "Initial Custom Field", CustomField.TYPE_NUMBER, Keychain.SYSTEM_KEY)),
			List.of(new ToyRequestDto("Will Be Skipped", "Ignored", new ArrayList<>())),
			List.of(new SystemRequestDto("Will Be Skipped", 3, false, new ArrayList<>()))
		);
		final BackupDataDto expectedBackupData = new BackupDataDto(
			List.of(initialCustomField),
			List.of(initialToy),
			List.of()
		);

		final ImportResultsDto results = gateway.importBackupData(backupData);

		System.out.println("BackupData after testImportCustomFields_MismatchedType_ReturnErrors");
		validateBackupData(expectedBackupData, gateway.getBackupData());
	}

	void testImportCustomFields_ValidFieldsAndInvalidTypeAndKey_ReturnSuccessAndErrors() {
		final BackupDataDto backupData = new BackupDataDto(
			//The toyCustomField will work but the invalidCustomField will throw an error preventing the rest of the import from completing.
			List.of(initialCustomField, toyCustomField, new CustomField(42, "Valid Name", "Invalid Type", "Invalid Key")),
			List.of(new ToyRequestDto("Will Be Skipped", "Ignored", new ArrayList<>())),
			List.of(new SystemRequestDto("Will Be Skipped", 3, false, new ArrayList<>()))
		);
		final BackupDataDto expectedBackupData = new BackupDataDto(
			List.of(initialCustomField, toyCustomField),
			List.of(initialToy),
			List.of()
		);

		final ImportResultsDto results = gateway.importBackupData(backupData);

		System.out.println("BackupData after testImportCustomFields_ValidFieldsAndInvalidTypeAndKey_ReturnSuccessAndErrors");
		validateBackupData(expectedBackupData, gateway.getBackupData());
	}

	void testImportToys_MissingCustomField_ReturnSuccessAndErrors() {
		//if a toy to be imported has a custom field value but is missing a matching custom field in the import that toy will be skipped
		final ToyRequestDto skippedToy = new ToyRequestDto("Valid Name", "Valid Set", List.of(new CustomFieldValue(0, "Missing Name", CustomField.TYPE_TEXT, "Value")));
		final BackupDataDto backupData = new BackupDataDto(
			List.of(initialCustomField, toyCustomField),
			List.of(initialToy, secondToy, skippedToy),
			List.of()
		);
		final BackupDataDto expectedBackupData = new BackupDataDto(
			List.of(initialCustomField, toyCustomField),
			List.of(initialToy, secondToy),
			List.of()
		);

		final ImportResultsDto results = gateway.importBackupData(backupData);

		System.out.println("BackupData after testImportToys_MissingCustomField_ReturnSuccessAndErrors");
		validateBackupData(expectedBackupData, gateway.getBackupData());
	}

	void testImportSystems_MissingCustomField_ReturnSuccessAndErrors() {
		//if a system to be imported has a custom field value but is missing a matching custom field in the import that system will be skipped
		final SystemRequestDto skippedSystem = new SystemRequestDto("Valid Name", 4, false, List.of(new CustomFieldValue(0, "Missing Name", CustomField.TYPE_TEXT, "Value")));
		final BackupDataDto backupData = new BackupDataDto(
			List.of(initialCustomField, toyCustomField),
			List.of(initialToy, secondToy),
			List.of(initialSystem, skippedSystem)
		);
		final BackupDataDto expectedBackupData = new BackupDataDto(
			List.of(initialCustomField, toyCustomField),
			List.of(initialToy, secondToy),
			List.of(initialSystem)
		);

		final ImportResultsDto results = gateway.importBackupData(backupData);

		System.out.println("BackupData after testImportSystems_MissingCustomField_ReturnSuccessAndErrors");
		validateBackupData(expectedBackupData, gateway.getBackupData());
	}

	void testImportSystems_SomeExistingSomeNew_ReturnSuccess() {
		final BackupDataDto backupData = new BackupDataDto(
			List.of(initialCustomField, toyCustomField, systemCustomField),
			List.of(initialToy, secondToy),
			List.of(initialSystem, secondSystem)
		);

		final ImportResultsDto results = gateway.importBackupData(backupData);

		System.out.println("BackupData after testImportSystems_SomeExistingSomeNew_ReturnSuccess");
		validateBackupData(backupData, gateway.getBackupData());
	}

	void importBackupData_UsingRetrievedBackupData_NoNewDataImported() {

		final BackupDataDto actualBackupData = gateway.getBackupData();
		final BackupDataDto expectedBackupData = new BackupDataDto(
			List.of(initialCustomField, toyCustomField, systemCustomField),
			List.of(initialToy, secondToy),
			List.of(initialSystem, secondSystem)
		);
		validateBackupData(expectedBackupData, actualBackupData);

		final ImportResultsDto results = gateway.importBackupData(actualBackupData);

		System.out.println("BackupData after importBackupData_UsingRetrievedBackupData_NoNewDataImported");
		validateBackupData(expectedBackupData, gateway.getBackupData());
	}

	private void validateBackupData(BackupDataDto expectedBackupData, BackupDataDto actualBackupData) {
		validateCustomFieldBackupData(expectedBackupData, actualBackupData);
		validateToyBackupData(expectedBackupData, actualBackupData);
		validateSystemBackupData(expectedBackupData, actualBackupData);
	}

	private void validateCustomFieldBackupData(BackupDataDto expectedData, BackupDataDto actualData) {
		final List<CustomField> expectedCustomFields = expectedData.customFields();
		final List<CustomField> actualCustomFields = actualData.customFields();
		if (null == expectedCustomFields || null == actualCustomFields) {
			return;
		}
		for(int i = 0; i < expectedCustomFields.size(); i++) {
			final CustomField expectedCustomField = expectedCustomFields.get(i);
			final CustomField actualCustomField = actualCustomFields.get(i);
//			System.out.println("Custom Field with name: " + actualCustomField.name());
//			System.out.println("expected custom field name: " + expectedCustomField.name() + " actual custom field name:" + actualCustomField.name());
//			System.out.println("expected custom field type: " + expectedCustomField.type() + " actual custom field type:" + actualCustomField.type());
//			System.out.println("expected custom field entityKey: " + expectedCustomField.entityKey() + " actual custom field entityKey:" + actualCustomField.entityKey());
		}
	}

	private void validateToyBackupData(BackupDataDto expectedData, BackupDataDto actualData) {
		final List<ToyRequestDto> expectedToys = expectedData.toys();
		final List<ToyRequestDto> actualToys = actualData.toys();
		if (null == expectedToys || null == actualToys) {
			return;
		}
		for(int i = 0; i < expectedToys.size(); i++) {
			final ToyRequestDto expectedToy = expectedToys.get(i);
			final ToyRequestDto actualToy = actualToys.get(i);
			System.out.println("Toy with name:" + actualToy.name());
//			System.out.println("expected toy name: " + expectedToy.name() + " actual toy name:" + actualToy.name());
//			System.out.println("expected toy set: " + expectedToy.set() + " actual toy set:" + actualToy.set());
			validateCustomFieldValues(expectedToy.customFieldValues(), actualToy.customFieldValues(), Keychain.TOY_KEY, actualToy.name());
		}
	}

	private void validateSystemBackupData(BackupDataDto expectedData, BackupDataDto actualData) {
		final List<SystemRequestDto> expectedSystems = expectedData.systems();
		final List<SystemRequestDto> actualSystems = actualData.systems();
		if (null == expectedSystems || null == actualSystems) {
			return;
		}
		for(int i = 0; i < expectedSystems.size(); i++) {
			final SystemRequestDto expectedSystem = expectedSystems.get(i);
			final SystemRequestDto actualSystem = actualSystems.get(i);
			System.out.println("System with name:" + actualSystem.name());
//			System.out.println("expected system name: " + expectedSystem.name() + " actual system name:" + actualSystem.name());
//			System.out.println("expected system generation: " + expectedSystem.generation() + " actual system generation:" + actualSystem.generation());
//			System.out.println("expected system handheld: " + expectedSystem.handheld() + " actual system handheld:" + actualSystem.handheld());
			validateCustomFieldValues(expectedSystem.customFieldValues(), actualSystem.customFieldValues(), Keychain.SYSTEM_KEY, actualSystem.name());
		}
	}

	private void validateCustomFieldValues(List<CustomFieldValue> expectedValues, List<CustomFieldValue> actualValues, String entityKey, String name) {
		for(int i = 0; i < expectedValues.size(); i++) {
			final CustomFieldValue expectedValue = expectedValues.get(i);
			final CustomFieldValue actualValue = actualValues.get(i);
			System.out.println("Value with name:" + actualValue.getCustomFieldName());
			System.out.println("expected value name: " + expectedValue.getCustomFieldName() + " actual value name: " + actualValue.getCustomFieldName());
			System.out.println("expected value type: " + expectedValue.getCustomFieldType() + " actual value type: " + actualValue.getCustomFieldType());
			System.out.println("expected value value: " + expectedValue.getValue() + " actual value value: " + actualValue.getValue());
			if (!Objects.equals(expectedValue.getValue(), actualValue.getValue())) {
				System.out.println("------------------MISMATCH------------------");
			}
		}
	}

}
