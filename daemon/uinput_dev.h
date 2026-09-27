#ifndef UINPUT_DEV_H
#define UINPUT_DEV_H
#include <stdint.h>
#include <unistd.h>
#include <sys/types.h>
#include <linux/input.h>
#include "keymap.h"

typedef struct {
    int fd;
    const keymap_t *keymap;
    uint8_t key_pressed[NUM_BUTTONS];
} uinput_ctx_t;

/* Initialize and create the virtual uinput device.
 * Returns 0 on success, negative value on error.
 */
int uinput_device_create(uinput_ctx_t *ctx, const keymap_t *km, const char *device_name);

/* Release all currently held keys */
void uinput_release_all(uinput_ctx_t *ctx);

/* Inject key state change:
 * button_id: 0..11
 * state: 1 (pressed) or 0 (released)
 * Writes EV_KEY + SYN_REPORT in a single atomic syscall.
 */
static inline int uinput_inject_button(uinput_ctx_t *ctx, uint8_t button_id, uint8_t state) {
    if (__builtin_expect(button_id >= NUM_BUTTONS, 0)) return -1;

    uint16_t keycode = ctx->keymap->keycodes[button_id];
    if (__builtin_expect(keycode == 0, 0)) return 0;

    uint8_t normalized = state ? 1 : 0;
    if (ctx->key_pressed[button_id] == normalized) {
        return 0; /* Ignore redundant duplicate state to prevent key chatter */
    }
    ctx->key_pressed[button_id] = normalized;

    struct input_event ev[2];
    ev[0].type = EV_KEY;
    ev[0].code = keycode;
    ev[0].value = normalized;
    ev[0].time.tv_sec = 0;
    ev[0].time.tv_usec = 0;

    ev[1].type = EV_SYN;
    ev[1].code = SYN_REPORT;
    ev[1].value = 0;
    ev[1].time.tv_sec = 0;
    ev[1].time.tv_usec = 0;

    ssize_t written = write(ctx->fd, ev, sizeof(ev));
    return (written == (ssize_t)sizeof(ev)) ? 0 : -1;
}

/* Destroy the virtual uinput device and close fd */
void uinput_device_destroy(uinput_ctx_t *ctx);

#endif /* UINPUT_DEV_H */
