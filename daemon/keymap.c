#include "keymap.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <ctype.h>

typedef struct {
    const char *name;
    uint16_t code;
} key_entry_t;

static const key_entry_t KEY_TABLE[] = {
    {"KEY_ESC", KEY_ESC},
    {"KEY_1", KEY_1},
    {"KEY_2", KEY_2},
    {"KEY_3", KEY_3},
    {"KEY_4", KEY_4},
    {"KEY_5", KEY_5},
    {"KEY_6", KEY_6},
    {"KEY_7", KEY_7},
    {"KEY_8", KEY_8},
    {"KEY_9", KEY_9},
    {"KEY_0", KEY_0},
    {"KEY_MINUS", KEY_MINUS},
    {"KEY_EQUAL", KEY_EQUAL},
    {"KEY_BACKSPACE", KEY_BACKSPACE},
    {"KEY_TAB", KEY_TAB},
    {"KEY_Q", KEY_Q},
    {"KEY_W", KEY_W},
    {"KEY_E", KEY_E},
    {"KEY_R", KEY_R},
    {"KEY_T", KEY_T},
    {"KEY_Y", KEY_Y},
    {"KEY_U", KEY_U},
    {"KEY_I", KEY_I},
    {"KEY_O", KEY_O},
    {"KEY_P", KEY_P},
    {"KEY_LEFTBRACE", KEY_LEFTBRACE},
    {"KEY_RIGHTBRACE", KEY_RIGHTBRACE},
    {"KEY_ENTER", KEY_ENTER},
    {"KEY_LEFTCTRL", KEY_LEFTCTRL},
    {"KEY_A", KEY_A},
    {"KEY_S", KEY_S},
    {"KEY_D", KEY_D},
    {"KEY_F", KEY_F},
    {"KEY_G", KEY_G},
    {"KEY_H", KEY_H},
    {"KEY_J", KEY_J},
    {"KEY_K", KEY_K},
    {"KEY_L", KEY_L},
    {"KEY_SEMICOLON", KEY_SEMICOLON},
    {"KEY_APOSTROPHE", KEY_APOSTROPHE},
    {"KEY_GRAVE", KEY_GRAVE},
    {"KEY_LEFTSHIFT", KEY_LEFTSHIFT},
    {"KEY_BACKSLASH", KEY_BACKSLASH},
    {"KEY_Z", KEY_Z},
    {"KEY_X", KEY_X},
    {"KEY_C", KEY_C},
    {"KEY_V", KEY_V},
    {"KEY_B", KEY_B},
    {"KEY_N", KEY_N},
    {"KEY_M", KEY_M},
    {"KEY_COMMA", KEY_COMMA},
    {"KEY_DOT", KEY_DOT},
    {"KEY_SLASH", KEY_SLASH},
    {"KEY_RIGHTSHIFT", KEY_RIGHTSHIFT},
    {"KEY_KPASTERISK", KEY_KPASTERISK},
    {"KEY_LEFTALT", KEY_LEFTALT},
    {"KEY_SPACE", KEY_SPACE},
    {"KEY_CAPSLOCK", KEY_CAPSLOCK},
    {"KEY_F1", KEY_F1},
    {"KEY_F2", KEY_F2},
    {"KEY_F3", KEY_F3},
    {"KEY_F4", KEY_F4},
    {"KEY_F5", KEY_F5},
    {"KEY_F6", KEY_F6},
    {"KEY_F7", KEY_F7},
    {"KEY_F8", KEY_F8},
    {"KEY_F9", KEY_F9},
    {"KEY_F10", KEY_F10},
    {"KEY_NUM_LOCK", KEY_NUMLOCK},
    {"KEY_SCROLLLOCK", KEY_SCROLLLOCK},
    {"KEY_KP7", KEY_KP7},
    {"KEY_KP8", KEY_KP8},
    {"KEY_KP9", KEY_KP9},
    {"KEY_KPMINUS", KEY_KPMINUS},
    {"KEY_KP4", KEY_KP4},
    {"KEY_KP5", KEY_KP5},
    {"KEY_KP6", KEY_KP6},
    {"KEY_KPPLUS", KEY_KPPLUS},
    {"KEY_KP1", KEY_KP1},
    {"KEY_KP2", KEY_KP2},
    {"KEY_KP3", KEY_KP3},
    {"KEY_KP0", KEY_KP0},
    {"KEY_KPDOT", KEY_KPDOT},
    {"KEY_F11", KEY_F11},
    {"KEY_F12", KEY_F12},
    {"KEY_KPENTER", KEY_KPENTER},
    {"KEY_RIGHTCTRL", KEY_RIGHTCTRL},
    {"KEY_KPSLASH", KEY_KPSLASH},
    {"KEY_RIGHTALT", KEY_RIGHTALT},
    {"KEY_HOME", KEY_HOME},
    {"KEY_UP", KEY_UP},
    {"KEY_PAGEUP", KEY_PAGEUP},
    {"KEY_LEFT", KEY_LEFT},
    {"KEY_RIGHT", KEY_RIGHT},
    {"KEY_END", KEY_END},
    {"KEY_DOWN", KEY_DOWN},
    {"KEY_PAGEDOWN", KEY_PAGEDOWN},
    {"KEY_INSERT", KEY_INSERT},
    {"KEY_DELETE", KEY_DELETE},
    /* Gamepad buttons */
    {"BTN_SOUTH", BTN_SOUTH},
    {"BTN_EAST", BTN_EAST},
    {"BTN_NORTH", BTN_NORTH},
    {"BTN_WEST", BTN_WEST},
    {"BTN_TL", BTN_TL},
    {"BTN_TR", BTN_TR},
    {"BTN_SELECT", BTN_SELECT},
    {"BTN_START", BTN_START},
    {"BTN_MODE", BTN_MODE},
    {"BTN_THUMBL", BTN_THUMBL},
    {"BTN_THUMBR", BTN_THUMBR},
    {NULL, 0}
};

void keymap_init_defaults(keymap_t *km) {
    if (!km) return;
    /* Row 0: Up row */
    km->keycodes[0] = KEY_Q;
    km->keycodes[1] = KEY_W;
    km->keycodes[2] = KEY_E;
    km->keycodes[3] = KEY_R;

    /* Row 1: Middle row */
    km->keycodes[4] = KEY_A;
    km->keycodes[5] = KEY_S;
    km->keycodes[6] = KEY_D;
    km->keycodes[7] = KEY_F;

    /* Row 2: Down row */
    km->keycodes[8] = KEY_Z;
    km->keycodes[9] = KEY_X;
    km->keycodes[10] = KEY_C;
    km->keycodes[11] = KEY_V;
}

uint16_t keycode_from_name(const char *name) {
    if (!name) return KEY_RESERVED;

    /* Trim leading whitespace */
    while (*name && isspace((unsigned char)*name)) name++;

    /* Support direct numeric keycode (e.g., 30 for KEY_A) */
    char *endptr = NULL;
    long val = strtol(name, &endptr, 10);
    if (endptr != name && (*endptr == '\0' || isspace((unsigned char)*endptr)) && val > 0 && val < 0x2ff) {
        return (uint16_t)val;
    }

    /* Check formatted KEY_ prefix or bare character (e.g., "A" -> "KEY_A") */
    char normalized[64];
    if (strncasecmp(name, "KEY_", 4) == 0) {
        snprintf(normalized, sizeof(normalized), "%s", name);
    } else if (strncasecmp(name, "BTN_", 4) == 0) {
        snprintf(normalized, sizeof(normalized), "%s", name);
    } else {
        snprintf(normalized, sizeof(normalized), "KEY_%s", name);
    }

    /* Convert to uppercase */
    for (int i = 0; normalized[i]; i++) {
        normalized[i] = toupper((unsigned char)normalized[i]);
    }

    for (const key_entry_t *entry = KEY_TABLE; entry->name != NULL; entry++) {
        if (strcmp(entry->name, normalized) == 0) {
            return entry->code;
        }
    }

    return KEY_RESERVED;
}

const char *keyname_from_code(uint16_t code) {
    for (const key_entry_t *entry = KEY_TABLE; entry->name != NULL; entry++) {
        if (entry->code == code) {
            return entry->name;
        }
    }
    static char buf[32];
    snprintf(buf, sizeof(buf), "KEY_RAW_%u", code);
    return buf;
}

static char *trim_whitespace(char *str) {
    while (*str && isspace((unsigned char)*str)) str++;
    if (*str == '\0') return str;
    char *end = str + strlen(str) - 1;
    while (end > str && isspace((unsigned char)*end)) {
        *end = '\0';
        end--;
    }
    return str;
}

int keymap_load_file(keymap_t *km, const char *filepath) {
    if (!km || !filepath) return -1;

    FILE *f = fopen(filepath, "r");
    if (!f) return -1;

    char line[256];
    int line_num = 0;

    while (fgets(line, sizeof(line), f)) {
        line_num++;
        char *p = trim_whitespace(line);

        /* Skip comments and empty lines */
        if (*p == '\0' || *p == '#' || *p == ';') continue;

        /* Look for key = value delimiter */
        char *eq = strchr(p, '=');
        if (!eq) {
            eq = strchr(p, ':');
        }
        if (!eq) continue;

        *eq = '\0';
        char *key_str = trim_whitespace(p);
        char *val_str = trim_whitespace(eq + 1);

        int button_id = -1;
        if (strncasecmp(key_str, "button_", 7) == 0) {
            button_id = atoi(key_str + 7);
        } else if (strncasecmp(key_str, "btn_", 4) == 0) {
            button_id = atoi(key_str + 4);
        } else if (strncasecmp(key_str, "b_", 2) == 0) {
            button_id = atoi(key_str + 2);
        } else if (strncasecmp(key_str, "up_", 3) == 0) {
            button_id = atoi(key_str + 3); /* 0..3 */
        } else if (strncasecmp(key_str, "mid_", 4) == 0) {
            button_id = 4 + atoi(key_str + 4); /* 4..7 */
        } else if (strncasecmp(key_str, "down_", 5) == 0) {
            button_id = 8 + atoi(key_str + 5); /* 8..11 */
        } else if (isdigit((unsigned char)*key_str)) {
            button_id = atoi(key_str);
        }

        if (button_id >= 0 && button_id < NUM_BUTTONS) {
            uint16_t code = keycode_from_name(val_str);
            if (code != KEY_RESERVED) {
                km->keycodes[button_id] = code;
            } else {
                fprintf(stderr, "[keymap] Warning: Unknown key name '%s' at line %d\n", val_str, line_num);
            }
        }
    }

    fclose(f);
    return 0;
}
