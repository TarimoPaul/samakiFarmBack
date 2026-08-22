# Backend — Spring Boot (Java) + GraphQL + REST

## Muundo wa API (kama ilivyoamuliwa)
- **REST** (`/api/**`) — Auth, User Management, Roles & Permissions PEKEE
- **GraphQL** (`/graphql`) — kila kitu kingine: Production Units, Cycles (na baadaye Feed, Water Quality, Daily Tasks, Finance)
- RBAC moja (`PermissionChecker`) inatumika NA REST NA GraphQL — chanzo kimoja cha ukweli, si logic mbili tofauti

## Kilichokamilika
- Schema (Flyway `V1__init_schema.sql` — ile ile ya awali, sasa kwa muundo wa Flyway)
- Entities za JPA: User (+isRoot), Role, Permission (+module/groupName), Farm, FarmUser, ProductionUnit, Species, Cycle, DailyTask
- Security: JWT filter + PermissionChecker (RBAC ya pamoja) + @PreAuthorize (REST) — angalia "RBAC" hapa chini
- REST: `/api/auth/login`, `/api/users` (create), `/api/roles` (create/list, list/update permissions)
- GraphQL: `productionUnits`, `cycles` (queries) + `createProductionUnit`, `createCycle` (mutations) — `createCycle` inaonyesha FR-3.2 (tarehe ya mavuno kiotomatiki) na FR-4.1 (daily_tasks kiotomatiki)

## RBAC (imesasishwa kufuata muundo wa mradi wa Lsms)
- **JWT haibebi tena ruhusa.** Inabeba tu `userId` + `isRoot` (+ `farmId`/`roleId`/`roleName` kwa muktadha wa UI). Kila request, `JwtAuthFilter` inasoma role/ruhusa **fresh kutoka DB** (cache: dakika 15 kwa mtumiaji wa kawaida, dakika 5 kwa ROOT) — ukibadilisha ruhusa za role, watumiaji wanapata mabadiliko papo hapo bila kulazimika ku-login tena.
- **ROOT ni flag (`users.is_root`), si jina la role.** "OWNER" sasa ni role ya kawaida inayopata ufikiaji wake kupitia ruhusa za wazi (angalia `seed/role_permissions.csv`), si bypass ya hardcoded. Mtumiaji wa ROOT (hana farm/role) anatengenezwa na `RbacDataInitializer` kutoka `app.root.*` properties (angalia `application.yml`).
- **Permissions zinapakiwa kutoka `seed/permissions.csv`** (idempotent) na **role-permission mapping kutoka `seed/role_permissions.csv` MARA MOJA TU** kwa role isiyo na ruhusa yoyote bado — role iliyoshabadilishwa na admin haiguswi tena kwenye restart (angalia `RbacDataInitializer`).
- REST controllers zinatumia `@PreAuthorize("hasAuthority('...')")` (angalia `MethodSecurityConfig`/`CustomPermissionEvaluator`); GraphQL resolvers zinaendelea kutumia `PermissionChecker.require(...)` (chanzo kimoja cha ukweli kati ya API mbili).
- Roles 4 za msingi (OWNER/FARM_MANAGER/WORKER/VIEWER) zinabaki hardcoded kwenye `V1__init_schema.sql` kwa sababu ya bootstrap ya shamba jipya — tofauti na Lsms (ambapo ROOT-pekee anaunda roles zote kupitia UI), lakini muundo wa ruhusa/caching/ROOT-bypass ni ule ule.

## MUHIMU — Lombok + JDK 23 kwenye Maven (fix iliyofanywa)
Lombok 1.18.34 (iliyosimamiwa na spring-boot-dependencies 3.3.2) ilikuwa ikishindwa **kimya kimya** kutengeneza getters/setters wakati wa `mvn compile` kwenye JDK 23 — makosa ya "cannot find symbol" kila mahali bila kosa lolote la Lombok lenyewe. Chanzo: "auto-discovery" ya annotation processor kutoka kwenye `-classpath` ndefu (~60 jars) kwenye njia yenye nafasi (`D:\KAMPUNI PROJECT\...`) ilishindwa. Suluhisho (tayari kwenye `pom.xml`): `<lombok.version>1.18.42</lombok.version>` + `<annotationProcessorPaths>` ya wazi kwenye maven-compiler-plugin. `mvn clean package` imejaribiwa na inafanya kazi.

## Bado Haijaandikwa
- Entities/resolvers za: FeedPurchase, FeedingLog, FeedStockMovement, WaterQualityLog, TaskCompletion, Reminder, Cost, Customer, Sale
- Reminders scheduler (Spring `@Scheduled` + AWS Pinpoint/SNS + Lambda Africa's Talking)
- Angular frontend (haijaguswa)

## MUHIMU — Kikwazo cha Ukaguzi

**Sikuweza kuendesha `mvn compile` wala kupakua dependencies za Maven** kwenye mazingira haya — mtandao wangu wa bash una orodha ya domains zilizoruhusiwa (npm, pypi, crates.io, github) na **Maven Central HAIPO kwenye orodha hiyo**. Kwa hiyo:
- Nimehakiki muundo wa juu wa faili (braces/parentheses zina uwiano, majina ya packages/imports yanaonekana sahihi kimantiki) — TU, si ukaguzi kamili wa Java compiler.
- **HAIJAJARIBIWA kuchemka (compile) wala kuendesha.** Kabla ya kuitumia, pakua kwenye kompyuta yako yenye Maven+JDK 17, endesha `mvn clean install`, rekebisha makosa yoyote ya kuchemka yatakayojitokeza (ni ya kawaida kwa code isiyojaribiwa bado).

## Jinsi ya Kujaribu (baada ya kupakua Maven/JDK 17 kwenye kompyuta yako)

1. Tengeneza database: `createdb samaki_db`
2. Weka environment variables: `DB_HOST`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET` (au badilisha `application.yml` moja kwa moja kwa majaribio ya local)
3. `mvn spring-boot:run`
4. Flyway itaendesha `V1__init_schema.sql` kiotomatiki wakati wa kuanza
5. Fungua `http://localhost:8080/graphiql` kujaribu GraphQL queries kwa mkono
6. Jaribu REST: `curl -X POST http://localhost:8080/api/auth/login -d '{"phone":"...","password":"..."}' -H "Content-Type: application/json"`

## AWS Deployment (baadaye)
Package kama Docker image (JAR ya Spring Boot + JRE 17 base image), deploy kwenye ECS Fargate — RDS PostgreSQL kama database, sawa na mpango wa awali wa AWS.
