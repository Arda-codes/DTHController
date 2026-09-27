CC ?= gcc
CFLAGS ?= -O3 -Wall -Wextra -pedantic -std=c11 -D_GNU_SOURCE
TARGET = rhythm-daemon
SRCS = main.c uinput_dev.c keymap.c
OBJS = $(SRCS:.c=.o)

PREFIX ?= /usr/local
BINDIR ?= $(PREFIX)/bin

.PHONY: all clean install uninstall

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CC) $(CFLAGS) -o $@ $(OBJS)

%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -f $(OBJS) $(TARGET)

install: $(TARGET)
	install -d $(DESTDIR)$(BINDIR)
	install -m 755 $(TARGET) $(DESTDIR)$(BINDIR)

uninstall:
	rm -f $(DESTDIR)$(BINDIR)/$(TARGET)
