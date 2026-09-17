# Council and Telegram — generated

Generated 2026-09-17T01:41:42-10:00. Do not edit by hand.

Ops desk: [INDEX.md](INDEX.md) · runners under `desk/`.

## Scheduler jobs (HST)

- `council-bruce-stats` — Bruce measured desk sample — cron hour="7,15,21", minute=18 → `council_bruce_stats.run`
- `governance-daily` — RootRecord governance daily — cron hour=10, minute=23 → `governance_daily.run`
- `governance-self-update` — Governance self-update after boot grace — interval 
                      hours=1,
                      start_date=datetime.now( → `governance_self_update.run`

## Source files + top-level functions

- `apps/council` — missing
### `tests/council/test_audio_request.py`

- `test_parse_voice`
- `test_help_lists_audio`

### `tests/council/test_boot_brief.py`

- `test_catchup_origin_is_not_coming_online`
- `test_decide_skip_same_boot`
- `test_decide_arm_when_never_run_and_uptime_long`
- `test_decide_run_fresh_boot_never_armed`
- `test_decide_run_after_reboot`
- `test_skip_churn_names`
- `test_select_dedupes_identical_bodies`
- `test_afternoon_skips_stale_morning`
- `test_refuses_september_4_current_pointer`
- `test_fresh_stamp_alone_does_not_make_stale_body_today`
- `test_boot_status_does_not_telegram_except_catchup`

### `tests/council/test_brainstorm.py`

- `test_min_ten_minutes`
- `test_parse_topic`
- `test_origin_continue_asks_for_pass_not_weather`
- `test_start_and_stop_phrases`
- `test_round_meta_files_only_on_wrap`
- `test_origin_rotates_round_jobs`
- `test_note_said_skips_pass_and_repeats`

### `tests/council/test_burst.py`

- `test_dm_burst_combines_and_persists`
- `test_idle_then_address_joins`
- `test_poll_timeout_shrinks_when_due`

### `tests/council/test_commands.py`

- `test_normalize_slash_strips_bot_suffix`
- `test_help_says_ava_menu_and_dm_split`
- `test_approve_regex_after_normalize`

### `tests/council/test_desk_read.py`

- `test_desk_read_allows_apps`
- `test_desk_read_allows_skill_desk`
- `test_desk_read_blocks_env_and_escape`
- `test_desk_read_ecoflow_alias`
- `test_kilauea_prompt_from_desk_files`
- `test_desk_facts_includes_relative_sources`
- `test_brainstorm_desk_omits_unrelated_live`

### `tests/council/test_dm_propose.py`

- `test_parse_propose_and_share_ask`
- `test_lift_uses_group_not_dm_transcript`
- `test_skip_nsfw_and_low_trust`

### `tests/council/test_handoff.py`

- `test_handoff_zip_is_under_handoff_dir`
- `test_handoff_rejects_env`
- `test_exec_keywords_handoff_and_reports`
- `test_bundle_includes_all_plan_names`
- `test_bundle_includes_ops_guide`

### `tests/council/test_heat.py`

- `_snap`
- `test_starts_at_zero_and_trust_gate`
- `test_level_bands`
- `test_play_prompt_follows_lead`
- `test_ava_faster_than_carly`
- `test_public_prompts_never_hold_back_or_erotica`
- `test_public_prompt_has_no_adult`
- `test_dm_70_plus_can_be_adult`
- `test_crush_band_not_erotica`
- `test_group_never_nsfw`
- `test_nsfw_swap_at_70_private_only`
- `test_carly_favor_penalizes`
- `test_underage_blocks`
- `test_bruce_not_in_heat`

### `tests/council/test_income_ops.py`

- `test_catalog_has_income_desks`
- `test_exec_keywords_income_desks`
- `test_help_includes_goals`
- `test_goal_tags_edit_store`
- `test_recipe_tags_save_user_source`
- `test_web_facts_allowlist`
- `test_ltc_forbids_send_and_waits_if_no_disk`
- `test_disk_session_dry_run`
- `test_disk_session_argv_owner_execute`

### `tests/council/test_join_welcome.py`

- `test_per_voice_trust_and_combined`
- `test_new_member_all_three_welcome_no_scores`
- `test_owner_join_not_lectured_as_forty`
- `test_announce_posts_all_voices`

### `tests/council/test_judgment.py`

- `test_parse_judge_tag`
- `test_strip_and_sanitize_hide_tag`
- `test_owner_frozen`
- `test_judge_steps_are_tiny_and_symmetric`
- `test_half_gain_from_even_score_accumulates`
- `test_replay_history_decimals`
- `test_hourly_cap_and_apply`
- `test_clamp_neg_budget`

### `tests/council/test_listen.py`

- `test_dm_route_is_that_bot_only`
- `test_should_handle_skips_group_on_bruce`
- `test_is_private_chat`

### `tests/council/test_ollama_continue.py`

- `_quiet_ram`
- `test_chat_continues_on_length`
- `test_chat_continues_twice_if_still_length`
- `test_ensure_solo_unloads_other_ggufs`
- `test_chat_keeps_model_when_keep_one_loaded`
- `test_chat_unloads_when_keep_one_off`
- `test_warm_default_skips_gguf_when_npu_chat`
- `test_chat_does_not_fall_back_to_ollama_when_flm_misses`

### `tests/council/test_ops_corrections.py`

- `test_dujuan_is_not_hawaii_threat`
- `test_ops_corrections_bind`

### `tests/council/test_people.py`

- `test_its_alex_sets_name`
- `test_mark_owner_not_handle`
- `test_observe_island_and_name`
- `test_agent_tags_shared`
- `test_typo_im_lonely_not_a_name`
- `test_secret_not_stored`

### `tests/council/test_power_status.py`

- `test_debounce_same_kind`
- `test_auto_idle_wake_not_every_flap`

### `tests/council/test_prompting.py`

- `test_feelings_prompt_has_mood_not_gauges`
- `test_reply_quote_included`
- `test_round_follow_brainstorm_skips_unrelated_desk`
- `test_round_follow_stays_on_ask`
- `test_carly_round_may_curse`
- `test_speak_prompt_forbids_dodge`
- `test_speak_prompt_pins_last_three`
- `test_carly_persona_may_curse`

### `tests/council/test_proposals.py`

- `test_plan_id_shape`
- `test_approve_hint_in_dropdown`
- `test_carly_round_prompt_is_security`
- `_wire_plans`
- `test_same_day_publish_amends_one_file`
- `test_human_note_appends_without_rewrite`
- `test_backfill_channel_appends_once`
- `test_backfill_appends_recovered_once`
- `test_looks_owner_implemented`
- `test_clear_implemented_drops_pending`

### `tests/council/test_quake_watch.py`

- `test_format_quake_uses_usgs_fields`
- `test_spoken_quake_expands_for_voice`
- `test_process_feed_seeds_without_posting`

### `tests/council/test_queue.py`

- `test_dedupe_same_voice_and_source`
- `test_done_history_does_not_block_enqueue`

### `tests/council/test_refer.py`

- `test_parse_offer_rejects_self_and_adult`
- `test_yes_dispatches_other_bot`
- `test_no_clears_offer`
- `test_team_prompt_forbids_dodge`

### `tests/council/test_report_cast.py`

- `test_readable_clip_script`
- `test_transcript_and_caption_explain_notes`
- `test_cast_posts_audio_then_transcript`
- `test_reply_saves_note`

### `tests/council/test_router.py`

- `test_question_keywords_team_round`
- `test_what_is_ava_still_answers`
- `test_hold_on_sara_silence`
- `test_third_person_ava_is_quiet`
- `test_ava_role_third_person`
- `test_hey_ava_vocative`
- `test_team_plus_named_bruce_only`
- `test_hey_guys_queues_all_three`
- `test_all_feeling_queues_all_three`
- `test_agents_and_ai_keywords`
- `test_thought_session_ava_starts_round`
- `test_pass_on_plus_reply_to_ava`
- `test_reply_continue_without_pass_on`
- `test_untagged_human_stays_quiet`
- `test_substantive_untagged_stays_quiet`
- `test_empathy_i_can_relate`
- `test_carly_tag`
- `test_bare_council_not_group`
- `test_over_to_carly_handoff`
- `test_good_job_everyone_thanks_carly`

### `tests/council/test_sanitize.py`

- `test_strips_file_markers`
- `test_strips_skill_and_gt`
- `test_strips_axis_lines`
- `test_json_fail_closed`
- `test_empty_fail_closed`
- `test_strips_own_handle_and_self_lead`
- `test_strips_mid_message_self_vocative`
- `test_strips_programmed_like_and_other_scores`
- `test_strips_bare_judge_and_wrap_leak`
- `test_strips_inline_judge`
- `test_strips_code_breaks_and_html`
- `test_strips_operator_name_unless_allowed`
- `test_refuses_other_member_gossip`
- `test_bruce_full_hawaiian_rewritten`
- `test_strips_engine_names_and_constraint_recap`
- `test_keeps_ordinary_answer`

### `tests/council/test_self_repair.py`

- `test_self_repair_window_and_hour_cap`
- `test_self_repair_prompt_block_empty_when_off`

### `tests/council/test_skills.py`

- `test_kiweh_injects_glossary`
- `test_exec_not_auto_matched_as_read`
- `test_skill_tokens_never_leave_sanitizer`
- `test_strips_own_bot_handle`
- `test_aina_and_ascii_hit_hawaiian_glossary`
- `test_aloha_and_mahalo_match`
- `test_kilauea_macron_equals_ascii`
- `test_plain_chat_does_not_inject_hawaiian`
- `test_clock_phrase_does_not_inject_hawaiian`
- `test_aloha_with_clock_does_not_add_hawaii_place_desk`
- `test_dict_lookup_when_hawaiian_word_appears`

### `tests/council/test_storm_plot.py`

- `test_storm_plot_dujuan_not_hawaii`
- `test_storm_plot_live_file_has_dujuan`

### `tests/council/test_telegram_chunks.py`

- `test_split_chunks_keeps_short`
- `test_split_chunks_breaks_on_paragraph`

### `tests/council/test_transfer_intake.py`

- `test_name_intake_hears_all_three`
- `test_caption_hash_stays_short`

### `tests/council/test_voice_models.py`

- `test_general_queries_stay_on_fast_chat_model`
- `test_brainstorm_stays_on_fast_chat_model`
- `test_coder_override_falls_back`


