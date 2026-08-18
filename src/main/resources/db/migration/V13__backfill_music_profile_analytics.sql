-- Conservatively seed durable profile counters from behavior events created before
-- playback sessions and actual-listening counters were introduced in V11.
-- INSERT IGNORE deliberately preserves any track already aggregated by the new path.
INSERT IGNORE INTO music_user_track_stat (
    user_id,
    track_key,
    play_count,
    complete_count,
    skip_count,
    repeat_count,
    total_playback_ms,
    first_played_at,
    last_played_at
)
SELECT behavior.user_id,
       item.track_key,
       SUM(CASE WHEN behavior.event_type IN ('PLAY_START', 'REPEAT') THEN 1 ELSE 0 END),
       SUM(CASE WHEN behavior.event_type = 'COMPLETE' THEN 1 ELSE 0 END),
       SUM(CASE WHEN behavior.event_type = 'SKIP' THEN 1 ELSE 0 END),
       SUM(CASE WHEN behavior.event_type = 'REPEAT' THEN 1 ELSE 0 END),
       SUM(
           CASE
               WHEN behavior.listened_ms IS NOT NULL THEN behavior.listened_ms
               WHEN behavior.event_type IN ('COMPLETE', 'SKIP') THEN COALESCE(behavior.playback_ms, 0)
               ELSE 0
           END
       ),
       MIN(behavior.created_at),
       MAX(behavior.created_at)
  FROM music_behavior_event behavior
  JOIN music_recommendation_item item ON item.id = behavior.recommendation_item_id
 WHERE behavior.playback_session_id IS NULL
 GROUP BY behavior.user_id, item.track_key;
