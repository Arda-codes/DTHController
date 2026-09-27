#include "uinput_dev.h"
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <linux/uinput.h>

int uinput_device_create(uinput_ctx_t *ctx, const keymap_t *km, const char *device_name) {
    if (!ctx || !km) return -1;

    ctx->keymap = km;
    ctx->fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (ctx->fd < 0) {
        ctx->fd = open("/dev/input/uinput", O_WRONLY | O_NONBLOCK);
    }

    if (ctx->fd < 0) {
        int err = errno;
        fprintf(stderr, "[uinput] Error: Failed to open /dev/uinput (%s)\n", strerror(err));
        if (err == EACCES || err == EPERM) {
            fprintf(stderr, "[uinput] Permission denied. Either run with sudo or configure udev rules:\n");
            fprintf(stderr, "[uinput]   echo 'KERNEL==\"uinput\", MODE=\"0660\", GROUP=\"input\", TAG+=\"uaccess\"' | sudo tee /etc/udev/rules.d/99-uinput.rules\n");
            fprintf(stderr, "[uinput]   sudo udevadm control --reload-rules && sudo udevadm trigger\n");
        }
        return -1;
    }

    /* Enable key events, repetition, and syn events */
    if (ioctl(ctx->fd, UI_SET_EVBIT, EV_KEY) < 0) {
        fprintf(stderr, "[uinput] Error: ioctl UI_SET_EVBIT EV_KEY failed (%s)\n", strerror(errno));
        close(ctx->fd);
        ctx->fd = -1;
        return -1;
    }

    if (ioctl(ctx->fd, UI_SET_EVBIT, EV_SYN) < 0) {
        fprintf(stderr, "[uinput] Error: ioctl UI_SET_EVBIT EV_SYN failed (%s)\n", strerror(errno));
        close(ctx->fd);
        ctx->fd = -1;
        return -1;
    }

    /* Do NOT enable EV_REP: rhythm game controllers must never auto-repeat keys */

    /* Enable standard keyboard keys (1..248) so systemd-udev classifies device as ID_INPUT_KEYBOARD=1.
     * This is required on Wayland (KWin / GNOME) for keystrokes to be routed to focused windows. */
    for (int code = 1; code <= 248; code++) {
        ioctl(ctx->fd, UI_SET_KEYBIT, code);
    }
    for (int code = BTN_MISC; code <= BTN_GEAR_UP; code++) {
        ioctl(ctx->fd, UI_SET_KEYBIT, code);
    }

    /* Configure virtual device properties */
    struct uinput_setup usetup;
    memset(&usetup, 0, sizeof(usetup));
    usetup.id.bustype = BUS_USB;
    usetup.id.vendor  = 0x1234; /* Virtual Vendor ID */
    usetup.id.product = 0x5678; /* Virtual Product ID */
    usetup.id.version = 1;
    snprintf(usetup.name, UINPUT_MAX_NAME_SIZE, "%s", device_name ? device_name : "Rhythm Game Controller");

    if (ioctl(ctx->fd, UI_DEV_SETUP, &usetup) < 0) {
        fprintf(stderr, "[uinput] Error: ioctl UI_DEV_SETUP failed (%s)\n", strerror(errno));
        close(ctx->fd);
        ctx->fd = -1;
        return -1;
    }

    if (ioctl(ctx->fd, UI_DEV_CREATE) < 0) {
        fprintf(stderr, "[uinput] Error: ioctl UI_DEV_CREATE failed (%s)\n", strerror(errno));
        close(ctx->fd);
        ctx->fd = -1;
        return -1;
    }

    memset(ctx->key_pressed, 0, sizeof(ctx->key_pressed));
    return 0;
}

void uinput_release_all(uinput_ctx_t *ctx) {
    if (!ctx || ctx->fd < 0) return;

    for (int i = 0; i < NUM_BUTTONS; i++) {
        if (ctx->key_pressed[i]) {
            uint16_t code = ctx->keymap->keycodes[i];
            if (code != 0) {
                struct input_event ev[2];
                ev[0].type = EV_KEY;
                ev[0].code = code;
                ev[0].value = 0;
                ev[0].time.tv_sec = 0;
                ev[0].time.tv_usec = 0;

                ev[1].type = EV_SYN;
                ev[1].code = SYN_REPORT;
                ev[1].value = 0;
                ev[1].time.tv_sec = 0;
                ev[1].time.tv_usec = 0;
                write(ctx->fd, ev, sizeof(ev));
            }
            ctx->key_pressed[i] = 0;
        }
    }
}

void uinput_device_destroy(uinput_ctx_t *ctx) {
    if (!ctx || ctx->fd < 0) return;

    /* Release any stuck keys before destroying */
    uinput_release_all(ctx);

    ioctl(ctx->fd, UI_DEV_DESTROY);
    close(ctx->fd);
    ctx->fd = -1;
}
