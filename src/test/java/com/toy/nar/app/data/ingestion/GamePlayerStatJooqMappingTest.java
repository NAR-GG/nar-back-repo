package com.toy.nar.app.data.ingestion;

import static com.toy.nar.jooq.tables.GamePlayerStat.GAME_PLAYER_STAT;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import com.toy.nar.app.data.ingestion.dto.GamePlayerStatInsertRow;
import com.toy.nar.jooq.tables.records.GamePlayerStatRecord;

class GamePlayerStatJooqMappingTest {

	@Test
	void mapsInsertRowToGeneratedJooqRecord() throws Exception {
		GamePlayerStatInsertRow row = rowWith(
				"gameParticipantId", 1L,
				"kills", 3,
				"isFirstBloodKill", true,
				"damageShare", 0.42d);

		DSLContext dsl = DSL.using(SQLDialect.MYSQL);
		GamePlayerStatRecord record = dsl.newRecord(GAME_PLAYER_STAT, row);

		assertThat(record.getGameParticipantId()).isEqualTo(1L);
		assertThat(record.getKills()).isEqualTo(3);
		assertThat(record.getIsFirstBloodKill()).isTrue();
		assertThat(record.getDamageShare()).isEqualTo(0.42d);
	}

	private GamePlayerStatInsertRow rowWith(Object... nameValuePairs) throws Exception {
		RecordComponent[] components = GamePlayerStatInsertRow.class.getRecordComponents();
		Object[] args = Arrays.stream(components)
				.map(component -> defaultValue(component.getType()))
				.toArray();

		for (int i = 0; i < nameValuePairs.length; i += 2) {
			String name = (String) nameValuePairs[i];
			Object value = nameValuePairs[i + 1];
			for (int j = 0; j < components.length; j++) {
				if (components[j].getName().equals(name)) {
					args[j] = value;
					break;
				}
			}
		}

		return GamePlayerStatInsertRow.class.getDeclaredConstructor(
				Arrays.stream(components)
						.map(RecordComponent::getType)
						.toArray(Class<?>[]::new))
				.newInstance(args);
	}

	private Object defaultValue(Class<?> type) {
		if (type.equals(Long.class)) {
			return 0L;
		}
		if (type.equals(Integer.class)) {
			return 0;
		}
		if (type.equals(Double.class)) {
			return 0d;
		}
		if (type.equals(Boolean.class)) {
			return false;
		}
		return null;
	}
}
