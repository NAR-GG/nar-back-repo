package com.toy.nar.app.data.ingestion;

import static com.toy.nar.jooq.tables.GameTeamStat.GAME_TEAM_STAT;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import com.toy.nar.app.data.ingestion.dto.GameTeamStatInsertRow;
import com.toy.nar.jooq.tables.records.GameTeamStatRecord;

class GameTeamStatJooqMappingTest {

	@Test
	void mapsInsertRowToGeneratedJooqRecord() throws Exception {
		GameTeamStatInsertRow row = rowWith(
				"gameId", 10L,
				"teamId", 20L,
				"teamKills", 13,
				"isFirstBlood", true,
				"damageToTowers", 7821);

		DSLContext dsl = DSL.using(SQLDialect.MYSQL);
		GameTeamStatRecord record = dsl.newRecord(GAME_TEAM_STAT, row);

		assertThat(record.getGameId()).isEqualTo(10L);
		assertThat(record.getTeamId()).isEqualTo(20L);
		assertThat(record.getTeamKills()).isEqualTo(13);
		assertThat(record.getIsFirstBlood()).isTrue();
		assertThat(record.getDamageToTowers()).isEqualTo(7821);
	}

	private GameTeamStatInsertRow rowWith(Object... nameValuePairs) throws Exception {
		RecordComponent[] components = GameTeamStatInsertRow.class.getRecordComponents();
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

		return GameTeamStatInsertRow.class.getDeclaredConstructor(
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
