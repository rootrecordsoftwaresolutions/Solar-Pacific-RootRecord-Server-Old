# Kilauea

Standalone local Kilauea/USGS/HVO processor.

- Implementation: `RootRecord Core Ops\Kilauea\kilauea.py`
- AVA boundary: `ava\apps\core\services\kilauea.py`
- Scheduler adapter: `ava\apps\core\crons\since_last_fire\kilauea.py`
- Reports: `RootRecord Core Ops\Reports\YYYY\Month\Month Dth, YYYY`
- Live state remains under AVA `Data\state\` for existing mobile, Desk, and
  status consumers.

The processor preserves HVO fetching, USGS event facts, alert-state writes,
report generation, public-draft queueing, and existing publication behavior.
