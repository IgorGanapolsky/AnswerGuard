"""PostHog audience filters for AnswerGuard analytics scripts.

AnswerGuard shares PostHog project 299775 with Random Timer. All reporting
scripts must scope queries to AnswerGuard events so revenue/funnel metrics
are not polluted by sibling-app telemetry.
"""

from __future__ import annotations

LIVE_EVENTS_PREDICATE = """
(
  (
    lower(coalesce(properties.environment, '')) IN ('production', 'live')
    OR lower(coalesce(properties.build_audience, '')) = 'live'
  )
  AND lower(coalesce(properties.build_type, 'release')) != 'debug'
  AND lower(coalesce(properties.runtime_target, 'device')) NOT IN ('simulator', 'emulator')
  AND coalesce(toString(properties.is_internal), 'false') != 'true'
  AND coalesce(toString(properties.distribution_channel), 'legacy') NOT IN (
    'testflight', 'non_play_install', 'dev', 'emulator', 'simulator', 'ui_test'
  )
)
"""

# Explicit AnswerGuard identity (set by native SDKs as app_name).
_ANSWERGUARD_APP_PREDICATE = """
(
  lower(coalesce(toString(properties.app_name), '')) = 'answerguard'
  OR coalesce(toString(properties.product_id), '') LIKE 'answerguard%'
  OR coalesce(toString(properties.product_id), '') LIKE 'com.igorganapolsky.answerguard%'
)
"""

# Exclude known Random Timer product IDs and paywall entry points when app_name
# was not yet stamped on older builds.
_NOT_RANDOM_TIMER_PREDICATE = """
(
  coalesce(toString(properties.product_id), '') NOT LIKE 'elite_tactical%'
  AND coalesce(toString(properties.product_id), '') NOT LIKE 'pro_base%'
  AND coalesce(toString(properties.product_id), '') NOT IN ('com.iganapolsky.randomtimer.pro')
  AND coalesce(toString(properties.entry_point), '') NOT IN (
    'range_gate', 'voice_gate', 'repeat_gate', 'sound_arsenal_gate',
    'qualified_training_gate', 'sound_gate'
  )
  AND coalesce(toString(properties.setting), '') NOT IN (
    'max_seconds', 'min_seconds', 'alarm_duration', 'repeat_enabled',
    'repeat_rounds', 'sound_type', 'voice_callouts_enabled', 'voice_gender',
    'use_extended_range', 'vibration_enabled', 'volume'
  )
)
"""

ANSWERGUARD_EVENTS_PREDICATE = f"""
(
  {LIVE_EVENTS_PREDICATE}
  AND ({_ANSWERGUARD_APP_PREDICATE} OR {_NOT_RANDOM_TIMER_PREDICATE})
)
"""
