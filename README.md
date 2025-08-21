# NAR.GG
LOL 대회 데이터 기반 분석 서비스  

리그 오브 레전드 e스포츠 데이터를 기반으로 챔피언 조합 분석, 매치업 승률, 경기 일정/기록 등을 확인할 수 있는 서비스입니다.  

## 기술 스택

| 분야      | 기술 |
|-----------|------|
| Backend   | Java, Spring Framework |
| Database  | MySQL (AWS RDS) |
| Infra     | AWS EC2, Docker |
| CI/CD     | GitHub Actions |
| Data      | 6시간 주기 데이터 자동 업데이트 |

## ERD
<img width="965" height="642" alt="스크린샷 2025-08-21 오후 2 37 41" src="https://github.com/user-attachments/assets/4c21b98c-debf-407e-86bb-1e4c91c2d915" />

## 주요 기능

### 메인 페이지
- 원하는 챔피언 조합을 선택할 수 있습니다.  
<img width="1221" height="838" alt="메인 페이지" src="https://github.com/user-attachments/assets/cda063dd-1efb-4fac-ae3c-2e655271ae8b" />

### 조합 페이지
- 선택한 조합의 결과 리스트를 제공합니다.  
<img width="1224" height="842" alt="조합 페이지" src="https://github.com/user-attachments/assets/a818255d-19ed-43b0-9302-829cb93be377" />

### 1vs1 매치업 페이지
- 라인전 승률 및 세부 지표를 확인할 수 있습니다.  
<img width="1242" height="843" alt="1vs1 매치업 페이지" src="https://github.com/user-attachments/assets/265ff643-cb6e-4655-8f80-2e2face49fec" />

### 일정 페이지
- LCK 경기 일정 및 결과를 확인할 수 있습니다.  
<img width="1203" height="832" alt="일정 페이지" src="https://github.com/user-attachments/assets/18165e35-68d1-435d-8c95-afe9e41385e3" />

### 경기기록 페이지
- 특정 경기의 세부 기록을 제공합니다.  
<img width="1222" height="840" alt="경기기록 페이지" src="https://github.com/user-attachments/assets/604541cd-9a07-4053-9ff1-c20687fc9d0b" />

### 경기리스트 페이지
- 6시간 주기로 업데이트되는 최신 경기를 확인할 수 있습니다.  
<img width="1252" height="837" alt="경기리스트 페이지" src="https://github.com/user-attachments/assets/e0a22d00-f700-42f3-994e-6ac2defa6c03" />

