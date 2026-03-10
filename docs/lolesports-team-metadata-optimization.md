# LoL Esports Team Metadata Optimization

## Scope
- Goal: remove the `updateTeamMetadataFromMatches` bottleneck caused by name/date fallback matching.
- Data scope that matters operationally: Google Drive CSV starts from `2025`.

## Applied Changes
- Added `team_external_identity` mapping table.
- Carried LoL Esports `externalTeamId` through `WorldsService -> MatchResultDto.TeamInfo`.
- Switched team metadata sync to `ID-first -> name/date fallback`.
- Added alias overrides for legacy team naming differences.
- Auto-created missing internal teams required for `2025+` coverage.
- Added `blue_external_team_id` / `red_external_team_id` to `league_match`.
- Backfilled existing `league_match` rows with external team ids.

## Benchmark
- Benchmark target: `LeagueMatchService.updateTeamMetadataFromMatches`
- Input: `league=LCK`, `date=2026-02`, `sampleSize=15`

### Before
- `elapsedMs=11191`
- `prepareStatementCount=8553`
- `entityLoadCount=18160`
- `entityUpdateCount=0`

### After
- `elapsedMs=66`
- `prepareStatementCount=11`
- `entityLoadCount=20`
- `entityUpdateCount=0`

## Current Coverage
- `2025`: unresolved `league_match` rows = `0`
- `2026`: unresolved `league_match` rows = `2`
  - both are `LPL` placeholder matches with `TBD`

## Remaining Historical Gaps
- Remaining unresolved rows are concentrated in `2023-2024` historical data.
- Main residual teams:
  - `RARE ATOM`
  - `Evil Geniuses LG`
  - `TSM`
  - `Astralis`
  - `CLG`
  - `TEAM WHALES`

## Operational Conclusion
- For the production problem scope (`2025+` CSV ingestion and recent LoL Esports metadata sync), the bottleneck is resolved.
