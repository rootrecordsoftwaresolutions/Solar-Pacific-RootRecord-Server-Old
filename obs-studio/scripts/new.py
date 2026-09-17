import obspython as obs

# Default settings
num_channels = 7
loop_minutes = 10
channel_prefix = "Channel "
interval_ms = 0
current_index = 0
is_running = False
channel_names = []

def script_description():
    return (
        "<b>Channel Cycler</b><br>"
        "Creates scenes (Channel 1–7 by default) and automatically loops through all of them "
        "in the specified total time (default: 10 minutes)."
    )

def script_defaults(settings):
    obs.obs_data_set_default_int(settings, "num_channels", 7)
    obs.obs_data_set_default_int(settings, "loop_minutes", 10)
    obs.obs_data_set_default_string(settings, "prefix", "Channel ")

def script_properties():
    props = obs.obs_properties_create()

    obs.obs_properties_add_int(props, "num_channels", "Number of Channels", 2, 50, 1)
    obs.obs_properties_add_int(props, "loop_minutes", "Total Loop Time (minutes)", 1, 120, 1)
    obs.obs_properties_add_text(props, "prefix", "Scene Name Prefix", obs.OBS_TEXT_DEFAULT)

    obs.obs_properties_add_button(props, "btn_create", "Create Channels", create_channels_clicked)
    obs.obs_properties_add_button(props, "btn_start", "Start Cycling", start_clicked)
    obs.obs_properties_add_button(props, "btn_stop", "Stop Cycling", stop_clicked)

    return props

def script_update(settings):
    global num_channels, loop_minutes, channel_prefix, interval_ms, channel_names

    num_channels = obs.obs_data_get_int(settings, "num_channels")
    loop_minutes = obs.obs_data_get_int(settings, "loop_minutes")
    channel_prefix = obs.obs_data_get_string(settings, "prefix")

    # Calculate interval so the full loop takes exactly loop_minutes
    total_ms = loop_minutes * 60 * 1000
    interval_ms = max(1000, total_ms // num_channels)  # at least 1 second

    # Rebuild expected names
    channel_names = [f"{channel_prefix}{i}" for i in range(1, num_channels + 1)]

    # Restart timer with new interval if currently running
    if is_running:
        obs.timer_remove(cycle_next)
        obs.timer_add(cycle_next, interval_ms)

def script_load(settings):
    script_update(settings)
    obs.script_log(obs.LOG_INFO, "Channel Cycler loaded")

def script_unload():
    stop_cycling()

def scene_exists(name):
    source = obs.obs_get_source_by_name(name)
    if source:
        obs.obs_source_release(source)
        return True
    return False

def create_channels_clicked(props, prop):
    created = 0
    for name in channel_names:
        if not scene_exists(name):
            scene = obs.obs_scene_create(name)
            if scene:
                obs.obs_scene_release(scene)
                created += 1
                obs.script_log(obs.LOG_INFO, f"Created scene: {name}")
            else:
                obs.script_log(obs.LOG_WARNING, f"Failed to create: {name}")
        else:
            obs.script_log(obs.LOG_INFO, f"Already exists: {name}")

    obs.script_log(obs.LOG_INFO, f"Finished. Created {created} new channel scene(s).")
    return True

def start_clicked(props, prop):
    start_cycling()
    return True

def stop_clicked(props, prop):
    stop_cycling()
    return True

def start_cycling():
    global is_running, current_index

    if is_running:
        return

    # Make sure at least some of the channels exist
    existing = [n for n in channel_names if scene_exists(n)]
    if not existing:
        obs.script_log(obs.LOG_WARNING, "No channel scenes found. Click 'Create Channels' first.")
        return

    is_running = True
    current_index = 0
    obs.timer_remove(cycle_next)  # safety
    obs.timer_add(cycle_next, interval_ms)
    obs.script_log(obs.LOG_INFO,
        f"Started cycling {len(existing)} channels. "
        f"Interval: {interval_ms/1000:.1f}s  |  Full loop: {loop_minutes} min")

    # Switch to the first one immediately
    cycle_next()

def stop_cycling():
    global is_running
    if is_running:
        obs.timer_remove(cycle_next)
        is_running = False
        obs.script_log(obs.LOG_INFO, "Cycling stopped")

def cycle_next():
    global current_index

    if not channel_names:
        return

    # Find the next existing channel (skip missing ones)
    attempts = 0
    while attempts < len(channel_names):
        name = channel_names[current_index]
        source = obs.obs_get_source_by_name(name)

        if source:
            obs.obs_frontend_set_current_scene(source)
            obs.obs_source_release(source)
            obs.script_log(obs.LOG_INFO, f"Switched to {name}")
            break
        else:
            obs.script_log(obs.LOG_WARNING, f"Scene missing: {name}")

        current_index = (current_index + 1) % len(channel_names)
        attempts += 1

    # Advance for next time
    current_index = (current_index + 1) % len(channel_names)