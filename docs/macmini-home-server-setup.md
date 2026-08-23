# 맥미니 M1 홈서버 구축 기록

## 개요

2026년 8월 13일 당근에서 맥미니 M1(16GB/512GB)을 68만원에 사서 사이드프로젝트 서버로 만들었다. 목표는 화면과 키보드 없이 혼자 부팅해서 Docker까지 올라오는 헤드리스 서버다. 콜드 부팅 50초 만에 Tailscale, SSH, Colima가 전부 자동으로 뜨는 상태로 끝냈다.

이 문서에는 기기 검수 방법, 서버 설정 명령어, 그리고 실제로 밟은 함정 네 개를 남긴다. 함정 네 개가 이 문서의 핵심이다. 전부 "설정을 했는데 동작하지 않는" 형태라 원인을 모르면 몇 시간을 태운다.

프로덕션 이전(MySQL, Elasticsearch)은 하지 않았다. 하룻밤 안정성을 확인한 뒤로 미뤘다.

## 구입 판단

2026년 8월 현재 중고 맥 시장은 비정상이다. 애플이 2026년 4월 M4 맥미니의 256GB 구성을 단종시키면서 시작가가 89만원에서 119만원으로 올랐다. 팀 쿡이 "맥미니 공급 제약이 수개월 이어질 수 있다"고 밝혔고, AI 에이전트용 수요까지 겹쳤다. 신품 하한선이 올라가니 구세대 중고가 같이 떠받쳐졌다.

세대별 중고 시세를 비교하면 M2를 고르는 게 맞았다.

| 모델 | 중고 시세 | 판단 |
|---|---|---|
| M1 16/512 | 60~65만 | 68만은 상단 |
| M2 16/512 | 60~68만 | M1과 동가, 3년 젊음 |
| M4 16/512 | 130~135만 | 신품 정가(119만)를 넘김 |

M2가 M1과 같은 값인데 메모리 대역폭이 68GB/s에서 100GB/s로 46% 높고 OS 지원도 2~3년 길다. 결과적으로 M1을 68만에 산 것은 5~8만원 비싸게 산 셈이다.

용량은 512GB를 골랐다. 애플 실리콘 맥미니는 낸드가 로직보드에 납땜돼 있어 나중에 늘릴 수 없다. 256GB는 서버 워크로드(Docker 이미지, MySQL 데이터, Elasticsearch 인덱스, Prometheus 시계열)를 감당하지 못한다. M2의 256GB 모델은 단일 낸드 칩이라 속도까지 절반(약 1,500MB/s)으로 떨어진다.

## 기기 검수

중고 기기의 하자는 인수 당일에 찾아야 판매자에게 말이 통한다. 검수를 설정보다 먼저 했다.

스펙과 활성화 잠금은 한 번에 확인한다.

```bash
system_profiler SPHardwareDataType | grep -iE "chip|memory|serial|activation"
```

결과는 Apple M1, 16GB, 시리얼 C07GC05ZQ6P0, 활성화 잠금 Enabled였다.

활성화 잠금이 Enabled로 나와서 잠깐 이전 주인 계정을 의심했지만 아니었다. 이전 주인의 잠금이면 "모든 콘텐츠 및 설정 지우기" 단계와 셋업 마법사에서 이전 주인의 Apple ID 비밀번호를 요구한다. 데스크탑까지 도달했다는 것 자체가 잠금이 없었다는 뜻이다. 셋업하면서 내 Apple ID로 로그인해 "나의 Mac 찾기"가 켜진 결과였다.

디스크 용량과 속도를 잰다.

```bash
df -h /
dd if=/dev/zero of=$HOME/ddtest bs=1m count=4096 conv=fsync 2>&1 | tail -1 && rm $HOME/ddtest
```

460Gi(512GB 정상), 2.92GB/s가 나왔다. `conv=fsync`를 빼면 페이지 캐시에 쓴 시간만 재서 실제보다 빠르게 나온다. M1의 512GB는 128GB 낸드 두 개를 병렬로 쓰기 때문에 2,500MB/s 이상이 정상이다. 1,500MB/s대가 나오면 단일 칩 구성이니 스펙을 다시 확인해야 한다.

판매 글에 "생활 기스 없는 민트급"이라고 적혀 있었다. 민트급은 조폐국에서 갓 찍어낸 동전이라는 뜻인데 판매자 자기 신고라 표준이 없다. 가격 협상에서 프리미엄으로 인정할 근거가 못 된다.

## 헤드리스 서버 설정

### 전원과 슬립

정전이 복구되면 스스로 부팅하고, 절대 잠들지 않아야 한다.

```bash
sudo pmset -a autorestart 1 sleep 0 disksleep 0 standby 0 womp 1
pmset -g
```

`autorestart 1`이 정전 복구 후 자동 부팅, `womp 1`이 Wake on LAN이다.

### 원격 접속

집 공유기의 포트를 여는 대신 Tailscale을 썼다. 포트포워딩으로 22번을 인터넷에 노출하면 몇 시간 안에 브루트포스 시도가 시작되고, 유동 IP 때문에 DDNS도 필요하고, 가정용 회선의 서버 운영은 ISP 약관에 걸린다. Tailscale은 아웃바운드 연결만 쓰기 때문에 이 세 가지를 전부 피한다.

```bash
brew install --cask tailscale
open -a Tailscale
```

로그인한 뒤 관리 콘솔에서 이 기기의 key expiry를 비활성화했다. 기본값인 6개월이 지나면 인증 키가 만료되어 원격 접속이 끊긴다. 서버에서는 반드시 꺼야 한다. 노트북과 아이폰은 재로그인하면 되니 켜둬도 된다.

컴퓨터 이름을 Tailscale 이름과 맞춘다.

```bash
sudo scutil --set ComputerName macmini
sudo scutil --set LocalHostName macmini
sudo scutil --set HostName macmini
```

이제 노트북에서 `ssh changha@macmini`로 붙는다. MagicDNS가 이름을 100.111.167.92로 해석하고, WireGuard 터널을 타고 맥미니의 sshd에 닿는다. 공유기 설정은 하나도 건드리지 않았다.

### SSH 하드닝

키로 붙는 것을 먼저 확인한 다음에 비밀번호 인증을 막는다. 순서를 뒤집으면 잠긴다.

```bash
# 노트북에서
ssh-copy-id changha@macmini
ssh changha@macmini    # 비번 안 물으면 성공
```

비밀번호 차단은 파일 하나로 처리했다.

```bash
sudo tee /etc/ssh/sshd_config.d/000-hardening.conf > /dev/null <<'EOF'
PasswordAuthentication no
KbdInteractiveAuthentication no
EOF

sudo sshd -T | grep -Ei "passwordauthentication|kbdinteractive"
sudo launchctl kickstart -k system/com.openssh.sshd
```

`sshd -T`는 파일 내용이 아니라 sshd가 실제로 해석한 최종 값을 출력한다. 재시작 전에 이걸로 확인해야 헛수고를 막는다.

### 자동 업데이트

macOS 업데이트를 통째로 끄면 1년 뒤 패치 안 된 서버가 된다. 재부팅을 유발하는 항목만 끄고 나머지는 켠다.

```bash
sudo defaults write /Library/Preferences/com.apple.SoftwareUpdate AutomaticCheckEnabled -bool true
sudo defaults write /Library/Preferences/com.apple.SoftwareUpdate AutomaticDownload -bool true
sudo defaults write /Library/Preferences/com.apple.SoftwareUpdate AutomaticallyInstallMacOSUpdates -bool false
sudo defaults write /Library/Preferences/com.apple.SoftwareUpdate CriticalUpdateInstall -bool true
sudo defaults write /Library/Preferences/com.apple.SoftwareUpdate ConfigDataInstall -bool true
sudo defaults write /Library/Preferences/com.apple.commerce AutoUpdate -bool false
```

`AutomaticallyInstallMacOSUpdates`가 새벽에 예고 없이 재부팅하는 범인이다. 반대로 `CriticalUpdateInstall`(보안 응답)은 대부분 재부팅 없이 적용되는 긴급 취약점 패치라 켜두는 게 이득이다.

대신 한 달에 한 번 수동으로 올린다.

```bash
softwareupdate -l
sudo softwareupdate -ia
```

메이저 버전 업그레이드는 최소 한두 달 기다린다. Homebrew, Colima, JDK 호환 문제가 초기에 몰린다.

### 컨테이너 런타임

Docker Desktop 대신 Colima를 썼다. 라이선스 문제가 없고 가볍다.

```bash
brew install colima docker docker-compose
colima start --cpu 6 --memory 10 --disk 100 --vm-type vz --mount-type virtiofs
docker run --rm hello-world
brew services start colima
```

`--vm-type vz`는 QEMU 대신 애플의 Virtualization.framework를, `--mount-type virtiofs`는 빠른 파일 공유를 쓴다. MySQL 볼륨처럼 디스크 I/O가 많은 워크로드에서 차이가 크다.

기존 서버가 t4g(Graviton2)라 이미 arm64 이미지를 쓰고 있었다. 맥미니도 arm64이므로 이미지와 compose 파일을 그대로 재사용할 수 있다.

## 밟은 함정 네 개

네 개 모두 "설정은 분명히 했는데 동작하지 않는" 형태였다. 원인을 모르면 한참 헤맨다.

### FileVault를 켜면 원격 복구가 불가능해진다

FileVault를 켜면 재부팅 후 디스크가 잠긴 채로 잠금 해제 화면에서 멈춘다. 이 상태에서는 macOS가 아직 올라오지 않아 SSH도, Docker도, 네트워크 서비스도 시작되지 않는다. 누군가 물리적으로 키보드를 쳐야 진행된다. `pmset autorestart 1`과 자동 로그인이 전부 무의미해진다.

애플 실리콘은 FileVault를 꺼도 내장 SSD를 항상 하드웨어 암호화한다. 키가 Secure Enclave에 있고, FileVault가 꺼져 있으면 부팅 시 자동으로 풀릴 뿐이다. 즉 FileVault는 물리적 도난만 막는다. 집 안에 놓인 서버의 실제 위협은 네트워크 쪽이므로 끄고 SSH 하드닝에 집중하는 편이 낫다.

`sudo fdesetup authrestart`로 재부팅하면 그 한 번은 자동 잠금 해제된다. 하지만 이건 직접 명령을 내릴 때만 동작한다. 정전이나 커널 패닉으로 갑자기 꺼지면 소용없는데, 그때가 정확히 자동 복구가 필요한 순간이다.

### PasswordAuthentication no만으로는 비밀번호 로그인이 안 막힌다

`PasswordAuthentication no`를 설정하고 sshd를 재시작했는데도 비밀번호를 물었다.

```
(changha@macmini) Password:
```

괄호가 붙은 이 프롬프트가 단서다. 순수 password 인증이면 `changha@macmini's password:` 형태로 나온다. 괄호 형식은 keyboard-interactive, 즉 PAM 경로다.

macOS의 `/etc/ssh/sshd_config.d/100-macos.conf`에 `UsePAM yes`가 들어 있고 `KbdInteractiveAuthentication`은 설정돼 있지 않아 기본값 yes로 열려 있었다. 두 항목은 별개라서 둘 다 꺼야 한다.

`sshd_config` 19행에 `Include /etc/ssh/sshd_config.d/*`가 있고 sshd는 먼저 읽은 설정을 채택한다(first-match-wins). 그래서 `100-macos.conf`보다 먼저 읽히도록 `000-hardening.conf`라는 이름을 썼다. OS 업데이트가 `sshd_config` 본문을 덮어써도 살아남는다는 장점도 있다.

### ssh host "명령"은 .zshrc도 .zprofile도 읽지 않는다

Homebrew를 설치할 때 안내대로 `.zprofile`에 `brew shellenv`를 넣었다. 대화형으로 SSH 접속하면 `docker`가 잘 동작하는데, 원격 명령으로 실행하면 실패했다.

```
$ ssh changha@macmini "uptime && docker ps"
zsh:1: command not found: docker
```

zsh가 읽는 파일은 셸의 종류에 따라 다르다.

| 파일 | 읽는 조건 |
|---|---|
| `.zshenv` | 항상 |
| `.zprofile` | 로그인 셸 |
| `.zshrc` | 대화형 셸 |

`ssh host "명령"`은 비대화형이면서 비로그인 셸이라 `.zshenv`만 읽는다. `.zshrc`에 옮겨도 해결되지 않는 이유가 이것이다.

```bash
ssh changha@macmini 'cat >> ~/.zshenv' <<'EOF'
export PATH="/opt/homebrew/bin:/opt/homebrew/sbin:$PATH"
EOF
```

heredoc 구분자를 `'EOF'`로 감싸야 `$PATH`가 로컬에서 확장되지 않고 그대로 전달된다. `brew shellenv`를 eval하는 대신 PATH를 직접 지정하면 셸을 띄울 때마다 서브프로세스를 실행하지 않아 가볍다.

### Tailscale은 로그인 시 자동 실행이 기본으로 꺼져 있다

가장 위험한 함정이다. 재부팅했더니 SSH가 아예 응답하지 않았다. 맥미니 화면을 직접 보니 데스크탑은 정상적으로 떠 있는데 메뉴 막대에 Tailscale 아이콘이 없었다.

Tailscale Settings의 "Launch Tailscale at login"이 기본으로 꺼져 있다. 첫 재부팅 때는 macOS의 앱 세션 복원 기능이 우연히 Tailscale을 다시 띄워줘서 정상 동작하는 것처럼 보였다. 그래서 문제를 늦게 발견했다.

Tailscale이 안 뜨면 원격 접속 경로 자체가 사라진다. 헤드리스 서버에서는 복구할 방법이 없어서 물리적으로 기기 앞에 가야 한다. 반드시 체크할 것. 같은 화면의 "Allow incoming connections"도 켜져 있어야 SSH를 받는다.

Colima는 `brew services`로 등록하는데, 사용자 launchd 에이전트라 자동 로그인이 되어야 뜬다. FileVault를 끄고 자동 로그인을 켜둔 것이 여기서도 전제가 된다.

## 최종 검증

재부팅한 뒤 노트북에서 명령 하나로 확인한다.

```bash
ssh changha@macmini "uptime && docker ps"
```

통과 기준은 네 가지다. 비밀번호를 묻지 않으면 SSH 키 인증이 정상이고, `uptime`이 출력되면 자동 부팅과 자동 로그인이 정상이고, `docker ps` 테이블이 나오면 Colima 자동 기동이 정상이다.

콜드 부팅 후 28초에 SSH가 붙었고 50초에 Docker까지 응답했다. Colima는 VM을 띄우기 때문에 SSH보다 20~30초 늦게 준비된다.

## 곁다리로 정리한 것

한영 전환키를 오른쪽 Command로 옮기려고 Karabiner-Elements를 설치했다가 결국 제거했다. macOS 기본 설정의 "수식어 키"는 좌우를 구분하지 않아서 Command를 바꾸면 양쪽이 다 바뀌고 `Cmd+C`가 죽는다. 그래서 Karabiner가 유일한 방법이다. 제거할 때 드라이버 확장이 `activated waiting for user` 상태로 남는데, SIP가 켜져 있으면 `systemextensionsctl uninstall`이 거부된다. SIP를 끄면 안 된다. 시스템 설정의 드라이버 확장에서 토글을 끄거나, 로드조차 안 된 상태라 그냥 둬도 무해하다.

노트북과 맥미니 사이에서 마우스 포인터가 제멋대로 넘어가는 것은 macOS 유니버설 컨트롤이다. `시스템 설정 > 디스플레이 > 고급`에서 "가장자리로 밀어서 근처 Mac 또는 iPad에 연결"을 끄면 된다. 배경화면을 클릭할 때 모든 창이 치워지는 것은 Sonoma에서 들어온 기능이고 `시스템 설정 > 데스크탑 및 Dock`의 "데스크탑을 보려면 클릭"을 "스테이지 매니저에서만"으로 바꾸면 꺼진다.

Apple 진단을 돌리려다 복구 모드로 잘못 들어갔다. 애플 실리콘에서는 종료 상태에서 전원 버튼을 계속 누르고 있다가 시동 옵션 화면이 뜨면 손을 뗀다. 여기서 "옵션" 톱니바퀴를 클릭하면 복구 모드로 가고, 아무것도 클릭하지 않고 `Command+D`를 누르면 진단으로 간다. 블루투스 키보드는 이 화면에서 인식되지 않는 경우가 있으니 유선 키보드를 쓰는 게 확실하다.

## 다음 단계

Apple 진단과 포트 전수 검사(TB3 2개, USB-A 2개, HDMI, 이더넷)가 남았다.

프로덕션 이전은 단계를 나눠서 간다. 리스크가 없는 것부터 한다.

1. GitHub Actions self-hosted runner. 지금 무료 러너(2 vCPU)에서 Gradle 빌드와 Docker 이미지 빌드를 하는데, 맥미니(8코어/16GB)로 옮기면 훨씬 빠르고 arm64 네이티브라 에뮬레이션도 없다. 리스크가 없는 순이익이다.
2. Prometheus와 Grafana. Micrometer 의존성 하나와 actuator 설정만으로 JVM 힙, GC, HikariCP 풀 사용률, HTTP 레이턴시가 전부 노출된다. 지금은 CPU 스파이크나 푸시 지연을 조사할 때마다 로그를 세고 있는데 그 작업이 사라진다.
3. 스테이징 환경을 2~3주 굴려서 집 회선이 서버를 버티는지 실측한다.
4. 안정적이면 프로덕션을 옮긴다. EC2는 한 달 병행한 뒤 해지한다.

프로덕션을 옮길 때 앱과 DB는 반드시 같이 옮긴다. 앱만 집으로 가져오고 DB를 Oracle에 남기면 쿼리 한 번마다 인터넷 왕복이 생긴다. 지금은 클라우드 사이라 빠르지만 집과 클라우드 사이는 차원이 다르다.

16GB 기준으로 올릴 수 있는 것에는 한계가 있다. macOS가 4GB를 쓰고, MySQL 2.5GB, Spring Boot 1.5GB, Elasticsearch 2.5GB, Prometheus 1GB, Grafana 0.3GB로 11.8GB다. Kafka를 추가하면 13.3GB로 빠듯하고, k8s 컨트롤플레인은 들어가지 않는다. 단일 노드 k8s는 스케줄링과 HA가 무의미하므로 학습 목적이 아니면 얹을 이유도 없다.
