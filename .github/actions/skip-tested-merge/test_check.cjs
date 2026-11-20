// Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later
const {test} = require('node:test');
const assert = require('node:assert/strict');
const {check} = require('./check.cjs');
const env = {
    GITHUB_EVENT_NAME: 'push', GITHUB_REF: 'refs/heads/develop',
    GITHUB_REPOSITORY: 'wireapp/kalium', GITHUB_SHA: 'abc123',
    GITHUB_WORKFLOW_REF: 'wireapp/kalium/.github/workflows/gradle-jvm-tests.yml@refs/heads/develop',
};
const run = {
    head_sha: env.GITHUB_SHA, event: 'merge_group', status: 'completed', conclusion: 'success',
    path: '.github/workflows/gradle-jvm-tests.yml', html_url: 'https://github.com/wireapp/kalium/actions/runs/1',
};
const quiet = () => {};
const response = runs => ({ok: true, json: async () => ({workflow_runs: runs})});

test('only an exact successful merge-group run skips tests', async () => {
    assert.equal(await check(env, async url => {
        assert.equal(url.searchParams.get('head_sha'), env.GITHUB_SHA);
        assert.equal(url.searchParams.get('event'), 'merge_group');
        assert.equal(url.searchParams.get('status'), 'success');
        assert.equal(url.pathname, '/repos/wireapp/kalium/actions/workflows/gradle-jvm-tests.yml/runs');
        return response([run]);
    }, quiet), true);
    for (const change of [{head_sha: 'other'}, {event: 'push'}, {status: 'in_progress'},
        {conclusion: 'failure'}, {path: '.github/workflows/other.yml'}]) {
        assert.equal(await check(env, async () => response([{...run, ...change}]), quiet), false);
    }
});

test('PRs, merge groups and non-develop pushes never call the API', async () => {
    for (const change of [{GITHUB_EVENT_NAME: 'pull_request'}, {GITHUB_EVENT_NAME: 'merge_group'},
        {GITHUB_EVENT_NAME: 'workflow_dispatch'}, {GITHUB_REF: 'refs/heads/other'}]) {
        assert.equal(await check({...env, ...change}, () => {throw new Error('Unexpected API call');}, quiet), false);
    }
});

test('no match, denied access, network failure and malformed responses run all tests', async () => {
    for (const request of [async () => response([]), async () => ({ok: false, status: 403}),
        async () => {throw new Error('Network error');}, async () => ({ok: true, json: async () => ({})})]) {
        assert.equal(await check(env, request, quiet), false);
    }
});

test('pagination finds a match beyond the first page', async () => {
    const pages = [];
    assert.equal(await check(env, async url => {
        const page = url.searchParams.get('page');
        pages.push(page);
        return response(page === '1' ? Array(100).fill({...run, head_sha: 'other'}) : [run]);
    }, quiet), true);
    assert.deepEqual(pages, ['1', '2']);
});

test('a re-run of a develop push runs all tests', async () => {
    let calls = 0;
    assert.equal(await check({...env, GITHUB_RUN_ATTEMPT: '2'}, async () => {calls++; return response([run]);}, quiet), false);
    assert.equal(calls, 0);
    assert.equal(await check({...env, GITHUB_RUN_ATTEMPT: '1'}, async () => response([run]), quiet), true);
});
