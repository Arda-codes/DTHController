#ifndef KEYMAP_H
#define KEYMAP_H

#include <stdint.h>
#include <linux/input.h>

#define NUM_BUTTONS 12

typedef struct {
    uint16_t keycodes[NUM_BUTTONS];
} keymap_t;

/* Initialize keymap with sane defaults (Q W E R / A S D F / Z X C V) */
void keymap_init_defaults(keymap_t *km);

/* Load keymap from file. Returns 0 on success, -1 on file open failure. */
int keymap_load_file(keymap_t *km, const char *filepath);

/* Find keycode by name (e.g., "KEY_Q", "KEY_SPACE", or "Q"). Returns KEY_RESERVED (0) if unknown. */
uint16_t keycode_from_name(const char *name);

/* Get string name of keycode. */
const char *keyname_from_code(uint16_t code);

#endif /* KEYMAP_H */
