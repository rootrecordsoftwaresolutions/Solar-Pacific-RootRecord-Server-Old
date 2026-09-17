# Earthquakes

Standalone local earthquake report processor.

- Implementation: `RootRecord Core Ops\Earthquakes\earthquake_hourly.py`
- AVA boundary: `ava\apps\core\services\earthquake_hourly_processor.py`
- Compatibility import: `ava\apps\core\services\earthquake_hourly.py`
- Scheduler: `ava\apps\core\crons\since_last_fire\earthquake_hourly.py`
- Text reports: `RootRecord Core Ops\Reports\YYYY\Month\Month Dth, YYYY`
- Audio remains under AVA generated media for the existing playback pipeline.

The processor preserves Hawaii-first and global earthquake fetching, changes
since last report, 24-hour M2.5+ summaries, state, audio, and Discord delivery.
