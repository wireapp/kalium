/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
/* Pinned-artifact diagnostic. Only the four event APIs are called; no call runtime starts. */
#include <assert.h>
#include <errno.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <avs/avs_wcall.h>

struct results {
    unsigned incoming, missed, closed;
    int video, ring, type, reason;
    unsigned time;
    char conversation[64];
};

static void remember(struct results *result, const char *conversation, unsigned time) {
    snprintf(result->conversation, sizeof(result->conversation), "%s", conversation);
    result->time = time;
}

static void incoming(const char *conversation, uint32_t time, const char *user, const char *client,
                     int video, int ring, int type, void *arg) {
    struct results *result = arg;
    assert(user && client);
    result->incoming++;
    result->video = video;
    result->ring = ring;
    result->type = type;
    remember(result, conversation, time);
}

static void missed(const char *conversation, uint32_t time, const char *user, const char *client,
                   int video, void *arg) {
    struct results *result = arg;
    assert(user && client);
    result->missed++;
    result->video = video;
    remember(result, conversation, time);
}

static void closed(int reason, const char *conversation, uint32_t time, const char *user,
                   const char *client, void *arg) {
    struct results *result = arg;
    assert(user && client);
    result->closed++;
    result->reason = reason;
    remember(result, conversation, time);
}

static const char *start = "{\"version\":\"3.0\",\"type\":\"GROUPSTART\",\"sessid\":\"fixture\",\"resp\":false}";
static const char *end = "{\"version\":\"3.0\",\"type\":\"CONFEND\",\"sessid\":\"fixture\",\"resp\":false}";
static const char *second_start = "{\"version\":\"3.0\",\"type\":\"GROUPSTART\",\"sessid\":\"second-fixture\",\"resp\":false}";
static const char *second_end = "{\"version\":\"3.0\",\"type\":\"CONFEND\",\"sessid\":\"second-fixture\",\"resp\":false}";
static const char *video = "{\"version\":\"3.0\",\"type\":\"SETUP\",\"sessid\":\"fixture\",\"resp\":false,\"sdp\":\"v=0\",\"props\":{\"videosend\":\"true\"}}";
static const char *reject = "{\"version\":\"3.0\",\"type\":\"REJECT\",\"sessid\":\"fixture\",\"resp\":false}";

static int process(WUSER_HANDLE handle, const char *payload, const char *conversation,
                   unsigned now, const char *user, const char *client) {
    return wcall_event_process(handle, (const uint8_t *)payload, strlen(payload), now, 99,
                               conversation, user, client, 3);
}

int main(int argc, char **argv) {
    assert(argc == 2);
    struct results first = {0}, second = {0};
    WUSER_HANDLE handle = wcall_event_create("self@example.com", "abc123", incoming, missed, closed, &first);
    assert(handle != WUSER_INVALID_HANDLE);
    wcall_event_start(handle);
    int error = 0;
    bool reused = false;
    if (!strcmp(argv[1], "incoming-video")) {
        assert(process(handle, video, "call@example.com", 100, "peer@example.com", "def456") == 0);
    } else if (!strcmp(argv[1], "missed")) {
        assert(process(handle, start, "call@example.com", 100, "peer@example.com", "def456") == 0);
        assert(process(handle, end, "call@example.com", 100, "peer@example.com", "def456") == 0);
    } else if (!strcmp(argv[1], "closed")) {
        assert(process(handle, end, "call@example.com", 100, "peer@example.com", "def456") == 0);
    } else if (!strcmp(argv[1], "recreate")) {
        assert(process(handle, start, "first@example.com", 100, "peer@example.com", "def456") == 0);
        wcall_event_end(handle);
        WUSER_HANDLE next = wcall_event_create("self@example.com", "abc123", incoming, missed, closed, &second);
        reused = next == handle;
        handle = next;
        wcall_event_start(handle);
        assert(process(handle, start, "second@example.com", 100, "peer@example.com", "def456") == 0);
    } else if (!strcmp(argv[1], "two-conversations")) {
        assert(process(handle, start, "first@example.com", 100, "peer@example.com", "def456") == 0);
        assert(process(handle, start, "second@example.com", 100, "peer@example.com", "def456") == 0);
        assert(process(handle, end, "second@example.com", 100, "peer@example.com", "def456") == 0);
    } else if (!strcmp(argv[1], "two-distinct-sessions")) {
        assert(process(handle, start, "first@example.com", 100, "peer@example.com", "def456") == 0);
        assert(process(handle, second_start, "second@example.com", 100, "peer@example.com", "def456") == 0);
        assert(process(handle, second_end, "second@example.com", 100, "peer@example.com", "def456") == 0);
    } else if (!strcmp(argv[1], "other-self-client-reject")) {
        assert(process(handle, reject, "call@example.com", 100, "self@example.com", "other-client") == 0);
    } else if (!strcmp(argv[1], "current-self-client-reject")) {
        assert(process(handle, reject, "call@example.com", 100, "self@example.com", "abc123") == 0);
    } else if (!strcmp(argv[1], "expired")) {
        error = process(handle, start, "call@example.com", 200, "peer@example.com", "def456");
        assert(error == ETIMEDOUT);
    } else if (!strcmp(argv[1], "malformed")) {
        error = process(handle, "{", "call@example.com", 100, "peer@example.com", "def456");
        assert(error != 0);
    } else {
        assert(false);
    }
    wcall_event_end(handle);
    printf("AVS_EVENT_RESULT={\"case\":\"%s\",\"incoming\":%u,\"missed\":%u,\"closed\":%u,"
           "\"video\":%d,\"ring\":%d,\"type\":%d,\"reason\":%d,\"time\":%u,\"conversation\":\"%s\","
           "\"reusedHandle\":%s,\"newContextCallbacks\":%u,\"processError\":%d}\n",
           argv[1], first.incoming, first.missed, first.closed, first.video, first.ring, first.type,
           first.reason, first.time, first.conversation, reused ? "true" : "false",
           second.incoming + second.missed + second.closed, error);
    return 0;
}
