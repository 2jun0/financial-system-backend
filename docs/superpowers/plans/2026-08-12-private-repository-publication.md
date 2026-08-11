# Private Repository Publication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 승인된 로컬 프로젝트를 `2jun0/financial-system-backend`라는 GitHub Private 저장소로 게시한다.

**Architecture:** 로컬 `main` 브랜치와 기존 두 커밋을 원본으로 사용한다. GitHub CLI로 사용자 인증을 복구한 뒤 빈 Private 저장소를 만들고 `origin`을 연결해 push하며, GitHub API 기반 조회 결과로 공개 범위와 기본 브랜치를 검증한다.

**Tech Stack:** Git, GitHub CLI (`gh`), Markdown

## Global Constraints

- GitHub 저장소 이름은 `financial-system-backend`로 한다.
- 저장소 소유자는 `2jun0`으로 한다.
- GitHub 공개 범위는 Private으로 한다.
- 기본 브랜치는 `main`으로 한다.
- 승인된 README와 설계 기록을 변경하지 않는다.
- 특정 애플리케이션 기술 스택이나 프로젝트 골격을 추가하지 않는다.

---

### Task 1: Private GitHub 저장소 게시

**Files:**
- Verify: `README.md`
- Verify: `docs/superpowers/specs/2026-08-11-repository-foundation-design.md`
- Verify: `docs/superpowers/plans/2026-08-12-private-repository-publication.md`

**Interfaces:**
- Consumes: 로컬 `main` 브랜치와 승인된 문서 커밋
- Produces: `origin`이 연결된 `2jun0/financial-system-backend` Private GitHub 저장소

- [ ] **Step 1: 로컬 저장소 상태 확인**

Run: `git status --short --branch && git branch --show-current && git log --oneline --max-count=3`

Expected: 현재 브랜치는 `main`이고 계획 문서 커밋 외에 예상하지 못한 변경이 없다.

- [ ] **Step 2: GitHub 계정 인증 복구**

Run: `gh auth login -h github.com -p https -w`

Expected: 브라우저 또는 device flow 인증이 완료되고 `gh auth status`가 사용자 `2jun0`의 유효한 인증을 표시한다.

- [ ] **Step 3: Private 원격 저장소 생성 및 push**

Run: `gh repo create 2jun0/financial-system-backend --private --source=. --remote=origin --push --description "금융 거래의 정합성·동시성·트랜잭션·멱등성·장애 복구 문제를 실험하는 백엔드 프로젝트"`

Expected: GitHub 저장소가 생성되고 로컬 `main`이 `origin/main`을 추적한다.

- [ ] **Step 4: 원격 저장소 속성 검증**

Run: `gh repo view 2jun0/financial-system-backend --json nameWithOwner,visibility,defaultBranchRef,url`

Expected: `nameWithOwner`는 `2jun0/financial-system-backend`, `visibility`는 `PRIVATE`, `defaultBranchRef.name`은 `main`이다.

- [ ] **Step 5: 로컬과 원격 커밋 일치 검증**

Run: `git fetch origin main && test "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" && git status --short --branch`

Expected: 명령이 exit code 0으로 종료되고 로컬 `main`과 `origin/main`의 커밋 SHA가 같으며 작업 트리가 깨끗하다.
