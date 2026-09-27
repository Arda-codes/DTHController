#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <signal.h>
#include <fcntl.h>
#include <getopt.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <sched.h>
#include <sys/resource.h>

#include "keymap.h"
#include "uinput_dev.h"

#define DEFAULT_PORT 54321
#define DEFAULT_CONFIG_FILE "controller.conf"
#define READ_BUF_SIZE 256

static volatile sig_atomic_t g_running = 1;

static void sig_handler(int signum) {
    (void)signum;
    g_running = 0;
}

static void print_usage(const char *prog) {
    printf("Usage: %s [options]\n", prog);
    printf("Options:\n");
    printf("  -p, --port <port>     TCP port to listen on (default: %d)\n", DEFAULT_PORT);
    printf("  -c, --config <file>   Keymap configuration file (default: %s)\n", DEFAULT_CONFIG_FILE);
    printf("  -h, --help            Show this help message\n");
}

static int setup_server_socket(int port) {
    int server_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (server_fd < 0) {
        perror("[daemon] socket failed");
        return -1;
    }

    int opt = 1;
    if (setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt)) < 0) {
        perror("[daemon] setsockopt SO_REUSEADDR failed");
        close(server_fd);
        return -1;
    }

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK); /* 127.0.0.1 */
    addr.sin_port = htons((uint16_t)port);

    if (bind(server_fd, (struct sockaddr *)&addr, sizeof(addr)) < 0) {
        perror("[daemon] bind failed");
        close(server_fd);
        return -1;
    }

    if (listen(server_fd, 4) < 0) {
        perror("[daemon] listen failed");
        close(server_fd);
        return -1;
    }

    return server_fd;
}

static void configure_client_socket(int client_fd) {
    int flag = 1;
    /* Disable Nagle algorithm: send packets immediately */
    if (setsockopt(client_fd, IPPROTO_TCP, TCP_NODELAY, &flag, sizeof(flag)) < 0) {
        perror("[daemon] setsockopt TCP_NODELAY failed");
    }

    /* Force quick ACKs: don't delay TCP acknowledgment */
#ifdef TCP_QUICKACK
    if (setsockopt(client_fd, IPPROTO_TCP, TCP_QUICKACK, &flag, sizeof(flag)) < 0) {
        /* Non-fatal on systems without TCP_QUICKACK */
    }
#endif

    int rcvbuf = 2048;
    setsockopt(client_fd, SOL_SOCKET, SO_RCVBUF, &rcvbuf, sizeof(rcvbuf));

    int tos = 0x10; // IPTOS_LOWDELAY
    setsockopt(client_fd, IPPROTO_IP, IP_TOS, &tos, sizeof(tos));
}

int main(int argc, char *argv[]) {
    int port = DEFAULT_PORT;
    const char *config_path = DEFAULT_CONFIG_FILE;

    static struct option long_options[] = {
        {"port",   required_argument, 0, 'p'},
        {"config", required_argument, 0, 'c'},
        {"help",   no_argument,       0, 'h'},
        {0, 0, 0, 0}
    };

    int opt;
    while ((opt = getopt_long(argc, argv, "p:c:h", long_options, NULL)) != -1) {
        switch (opt) {
            case 'p':
                port = atoi(optarg);
                if (port <= 0 || port > 65535) {
                    fprintf(stderr, "Error: Invalid port %s\n", optarg);
                    return 1;
                }
                break;
            case 'c':
                config_path = optarg;
                break;
            case 'h':
                print_usage(argv[0]);
                return 0;
            default:
                print_usage(argv[0]);
                return 1;
        }
    }

    /* Set up signal handlers for graceful shutdown */
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = sig_handler;
    sigaction(SIGINT, &sa, NULL);
    sigaction(SIGTERM, &sa, NULL);
    signal(SIGPIPE, SIG_IGN); /* Ignore broken pipe on client disconnect */

    /* Elevate scheduling priority: attempt real-time SCHED_RR, fallback to nice -20 */
    struct sched_param sp;
    memset(&sp, 0, sizeof(sp));
    sp.sched_priority = 10;
    if (sched_setscheduler(0, SCHED_RR, &sp) == 0) {
        printf("[daemon] Real-time SCHED_RR priority enabled.\n");
    } else {
        setpriority(PRIO_PROCESS, 0, -20);
    }

    /* Initialize key mapping */
    keymap_t km;
    keymap_init_defaults(&km);

    /* Try loading configuration file if present */
    if (access(config_path, R_OK) == 0) {
        if (keymap_load_file(&km, config_path) == 0) {
            printf("[keymap] Loaded custom key mappings from: %s\n", config_path);
        } else {
            fprintf(stderr, "[keymap] Warning: Failed to parse %s, using defaults\n", config_path);
        }
    } else {
        printf("[keymap] Config '%s' not found, using default key mappings\n", config_path);
    }

    /* Print diagnostics: Active key mapping */
    printf("\n=== DTHController (Digital Twin : Harmonix) ===\n");
    printf("Row 0 (Up):     [0]=%s [1]=%s [2]=%s [3]=%s\n",
           keyname_from_code(km.keycodes[0]), keyname_from_code(km.keycodes[1]),
           keyname_from_code(km.keycodes[2]), keyname_from_code(km.keycodes[3]));
    printf("Row 1 (Middle): [4]=%s [5]=%s [6]=%s [7]=%s\n",
           keyname_from_code(km.keycodes[4]), keyname_from_code(km.keycodes[5]),
           keyname_from_code(km.keycodes[6]), keyname_from_code(km.keycodes[7]));
    printf("Row 2 (Down):   [8]=%s [9]=%s [10]=%s [11]=%s\n",
           keyname_from_code(km.keycodes[8]), keyname_from_code(km.keycodes[9]),
           keyname_from_code(km.keycodes[10]), keyname_from_code(km.keycodes[11]));
    printf("===============================================\n\n");

    /* Create virtual uinput device */
    uinput_ctx_t uinput_ctx;
    if (uinput_device_create(&uinput_ctx, &km, "DTHController") < 0) {
        fprintf(stderr, "[daemon] Fatal: Could not create uinput device.\n");
        return 1;
    }
    printf("[uinput] Virtual DTHController device created successfully.\n");

    /* Create and bind server socket */
    int server_fd = setup_server_socket(port);
    if (server_fd < 0) {
        uinput_device_destroy(&uinput_ctx);
        return 1;
    }

    printf("[daemon] Listening on 127.0.0.1:%d\n", port);
    printf("[daemon] Ensure ADB reverse is active: adb reverse tcp:%d tcp:%d\n", port, port);
    printf("[daemon] Ready for incoming controller connections. (Press Ctrl+C to stop)\n");
    fflush(stdout);

    /* Connection accept loop */
    while (g_running) {
        struct sockaddr_in client_addr;
        socklen_t client_len = sizeof(client_addr);
        int client_fd = accept(server_fd, (struct sockaddr *)&client_addr, &client_len);

        if (client_fd < 0) {
            if (errno == EINTR) continue;
            perror("[daemon] accept error");
            break;
        }

        configure_client_socket(client_fd);
        printf("[daemon] Client connected, ensuring all keys released\n");
        uinput_release_all(&uinput_ctx);

        /* Read buffer for hot path */
        uint8_t buf[READ_BUF_SIZE];
        size_t pending = 0;

        /* Process client events loop */
        while (g_running) {
            ssize_t n = read(client_fd, buf + pending, sizeof(buf) - pending);
            if (n <= 0) {
                if (n < 0 && (errno == EINTR || errno == EAGAIN)) {
                    continue;
                }
                /* Client disconnected: ALWAYS release all pressed keys */
                printf("[daemon] Client disconnected, releasing all held keys\n");
                uinput_release_all(&uinput_ctx);
                break;
            }

            size_t total = pending + (size_t)n;
            size_t i = 0;

            /* Process messages */
            while (i < total) {
                uint8_t op = buf[i];
                if (__builtin_expect(op < NUM_BUTTONS, 1)) {
                    /* Standard 2-byte event: [button_id (0..11), state (0 or 1)] */
                    if (i + 1 >= total) break; // Need full 2 bytes
                    uint8_t state = buf[i + 1];
                    if (state <= 1) {
                        uinput_inject_button(&uinput_ctx, op, state);
                        i += 2;
                    } else {
                        /* Corrupt byte / desync recovery: skip 1 byte to realign */
                        i++;
                    }
                } else if (op == 0xFE) {
                    /* Remap packet: [0xFE, button_id, keycode_hi, keycode_lo] */
                    if (i + 3 >= total) break; // Need full 4 bytes
                    uint8_t btn = buf[i + 1];
                    uint16_t code = ((uint16_t)buf[i + 2] << 8) | buf[i + 3];
                    if (btn < NUM_BUTTONS) {
                        /* If button is held down under old keycode, release it first */
                        if (uinput_ctx.key_pressed[btn]) {
                            uinput_inject_button(&uinput_ctx, btn, 0);
                        }
                        km.keycodes[btn] = code;
                        printf("[keymap] Dynamic remap: Button %u -> %s (%u)\n", btn, keyname_from_code(code), code);
                    }
                    i += 4;
                } else if (op == 0xFC) {
                    /* Ping probe packet: [0xFC, seq] -> reply [0xFD, seq] */
                    if (i + 1 >= total) break;
                    uint8_t pong[2] = {0xFD, buf[i + 1]};
                    write(client_fd, pong, 2);
                    i += 2;
                } else {
                    /* Unknown byte / desync recovery: skip 1 byte */
                    i++;
                }
            }

            /* Shift leftover pending bytes to front of buffer */
            if (i < total) {
                pending = total - i;
                memmove(buf, buf + i, pending);
            } else {
                pending = 0;
            }

#ifdef TCP_QUICKACK
            /* Re-arm quickack if needed */
            int flag = 1;
            setsockopt(client_fd, IPPROTO_TCP, TCP_QUICKACK, &flag, sizeof(flag));
#endif
        }

        close(client_fd);
    }

    printf("\n[daemon] Shutting down...\n");
    close(server_fd);
    uinput_device_destroy(&uinput_ctx);
    printf("[daemon] Cleaned up virtual device and exited.\n");

    return 0;
}
