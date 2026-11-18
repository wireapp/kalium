// Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later
// Only an exact successful merge-queue run of this workflow can replace push tests.
async function check(env = process.env, request = fetch, log = console.log) {
    if (env.GITHUB_EVENT_NAME !== 'push' || env.GITHUB_REF !== 'refs/heads/develop') {
        log('Run full tests: this is not a develop push.');
        return false;
    }
    try {
        const prefix = `${env.GITHUB_REPOSITORY}/`;
        const ref = env.GITHUB_WORKFLOW_REF;
        if (!ref?.startsWith(prefix)) throw new Error('Missing workflow reference');
        const workflow = ref.slice(prefix.length, ref.lastIndexOf('@'));
        if (!workflow.startsWith('.github/workflows/')) throw new Error('Invalid workflow path');
        for (let page = 1; ; page++) {
            const url = new URL(`${env.GITHUB_API_URL || 'https://api.github.com'}/repos/${env.GITHUB_REPOSITORY}/actions/workflows/${encodeURIComponent(workflow.split('/').pop())}/runs`);
            url.search = new URLSearchParams({event: 'merge_group', status: 'success', head_sha: env.GITHUB_SHA, per_page: '100', page: String(page)});
            const response = await request(url, {
                headers: {
                    Accept: 'application/vnd.github+json',
                    Authorization: `Bearer ${env.GH_TOKEN}`,
                    'X-GitHub-Api-Version': '2022-11-28',
                },
                signal: AbortSignal.timeout(15000),
            });
            if (!response.ok) throw new Error(`GitHub API returned ${response.status}`);
            const {workflow_runs: runs} = await response.json();
            if (!Array.isArray(runs)) throw new Error('Invalid workflow run response');
            const match = runs.find(run => run.head_sha === env.GITHUB_SHA && run.event === 'merge_group' &&
                run.status === 'completed' && run.conclusion === 'success' && run.path === workflow);
            if (match) {
                log(`Skip tests; warm develop cache: ${match.html_url} passed merge_group for ${env.GITHUB_SHA} (${workflow}).`);
                return true;
            }
            if (runs.length < 100) break;
        }
        log(`Run full tests: no successful merge_group run for ${env.GITHUB_SHA} in ${workflow}.`);
    } catch (error) {
        log(`Run full tests: merge-group lookup failed (${error.message}).`);
    }
    return false;
}

module.exports = {check};
if (require.main === module) {
    check().then(skip => console.log(`skip-tests=${skip}`));
}
