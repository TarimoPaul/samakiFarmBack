# Backend — Spring Boot (Java) + GraphQL + REST

## Muundo wa API
- **REST** (`/api/**`) — Auth, User Management, Roles & Permissions, Farms
- **GraphQL** (`/graphql`) — kila kitu kingine: Production Units, Cycles (na baadaye Feed, Water Quality, Daily Tasks, Finance)
- RBAC moja (`PermissionChecker`) inatumika NA REST NA GraphQL — chanzo kimoja cha ukweli, si logic mbili tofauti

Code imepangwa kwa **module** (si kwa tabaka): kila dhana ina folder yake yenye `entity/`, `dto/`, `repository/`, `services/`, na `controller/` au `graphql/`. Module mpya ni kuongeza folder tu.

## Mfumo wa umiliki na akaunti

Mfumo ni wa **kampuni moja** yenye mashamba yanayoweza kuwa zaidi ya moja.

- **`users`** = mtu (utambulisho + hali ya akaunti). **`farm_users`** = uanachama (`user_id` + `farm_id` + `role_id`). Mtu anaweza kuwa kwenye mashamba mengi, na role yake ni ya kila shamba — si ya mtu.
- **`users.status`** ni mzunguko wa maisha: `PENDING_APPROVAL` → `ACTIVE` → `DISABLED`. Ni tofauti kabisa na `is_deleted` (ambayo ni kufuta rekodi pekee).
- **Kujisajili** (`POST /api/auth/register`) kunaunda mtu `PENDING_APPROVAL` **pekee** — hakuna shamba, hakuna role, hakuna token.
- **Kuidhinisha** (`POST /api/users/{id}/approve`) kunahitaji ruhusa ya `approve_users` na kunabadilisha hali kuwa `ACTIVE`. **Hakutoi role.**
- **Kupewa uanachama** (`POST /api/users/{id}/memberships`) ni hatua tofauti kabisa (`manage_users`). Mtu `ACTIVE` asiye na uanachama anaingia na kuona ukurasa mtupu — hii ni hali halali.

### Mkataba wa login

Password inathibitishwa **KWANZA**, kisha hali ya akaunti inaangaliwa. Mpangilio huu ndio unaozuia mtu kugundua ni namba zipi zilizosajiliwa.

| Hali | HTTP | `errorCode` |
|---|---|---|
| Password batili / mtu hayupo / amefutwa | `401` | `INVALID_CREDENTIALS` |
| Password sahihi + `PENDING_APPROVAL` | `403` | `PENDING_APPROVAL` |
| Password sahihi + `DISABLED` | `403` | `ACCOUNT_DISABLED` |
| Password sahihi + `ACTIVE` | `200` | — (token + `mustChangePassword`) |

Frontend inatawi kwa **`errorCode`**, si kwa ujumbe wa Kiswahili.

### Vizuizi vya akaunti kwenye kila request

Login si mahali pekee hali ya akaunti inapokaguliwa. `JwtAuthFilter` inaikagua **kila request** (REST na GraphQL), hivyo token halali isiyoisha muda inaweza kukataliwa papo hapo:

| Hali ya mwenye token | HTTP | `errorCode` |
|---|---|---|
| `DISABLED` | `403` | `ACCOUNT_DISABLED` |
| `PENDING_APPROVAL` | `403` | `PENDING_APPROVAL` |
| `must_change_password = true` | `403` | `MUST_CHANGE_PASSWORD` |
| Amefutwa / hayupo | `401` | — (kama asiye na token) |

`MUST_CHANGE_PASSWORD` **haizuii `/api/auth/**`** — vinginevyo mtu angekwama bila njia ya kujinasua.

Misimbo hii inaandikwa na `JwtAuthFilter` ndani ya filter chain, ambayo **hairudishi ombi kwenye chain** likishakatwa. Kwa hiyo haiwezi kufunikwa na misimbo ya jumla hapa chini — hali ya akaunti inashinda kila wakati.

### Kikao na ruhusa

Misimbo miwili ya jumla, moja kwa kila swali. `errorCode` ni ile ile REST na GraphQL:

| Swali | Hali | HTTP | `errorCode` |
|---|---|---|---|
| Umeingia? | Token haipo, imeisha muda, au si sahihi | `401` | `UNAUTHENTICATED` |
| Unaruhusiwa? | Umeingia, lakini huna ruhusa husika | `403` | `FORBIDDEN` |

- `UNAUTHENTICATED` ni **tofauti na `INVALID_CREDENTIALS`** kimakusudi: huo ni "password mbaya kwenye fomu ya login", huu ni "hakuna kikao — futa token, rudi kwenye login". Kuzichanganya kungefanya token iliyoisha muda ionekane kama password mbaya.
- Vyanzo viwili vya kila msimbo vinatoa **envelope moja**: `SecurityConfig` (filter chain) na `GlobalExceptionHandler`/`PermissionChecker` (ndani ya ombi).
- Kwenye GraphQL msimbo uko kwenye `errors[].extensions.errorCode`. `classification` inabaki `UNAUTHORIZED`/`FORBIDDEN` kwa sababu `ErrorType` ya Spring GraphQL haina `UNAUTHENTICATED` — **tawi kwa `errorCode`**, si kwa `classification`.
- `/graphql` bila token kabisa inakatwa na filter chain, hivyo inarudisha envelope ya kawaida ya `401` badala ya umbo la `errors[]`.

### Kubadilisha password ukiwa umeingia

`POST /api/auth/change-password` — **hakuna OTP/SMS**. Uthibitisho ni token halali + password ya sasa:

```json
{ "currentPassword": "...", "newPassword": "..." }
```

- Password ya sasa mbaya → `401 INVALID_CREDENTIALS` (hakuna kinachobadilika)
- Password mpya sawa na ya sasa → `400` (vinginevyo lazima ya kubadilisha isingekuwa na maana)
- Ikifanikiwa → `must_change_password` inakuwa `false`, na **token ile ile inaendelea kufanya kazi** (haibebi chochote kuhusu password)

Hii ndiyo njia ya ROOT aliyetengenezwa kutoka environment variable kujinasua — bila kutegemea huduma ya SMS. `/api/auth/reset-password` (OTP) inabaki kwa mtu aliyesahau password yake.

## RBAC

- **JWT haibebi ruhusa.** Inabeba `userId` + `isRoot` (+ `farmId`/`roleId`/`roleName` kwa muktadha wa UI). Kila request, `JwtAuthFilter` inasoma hali ya akaunti na ruhusa **fresh kutoka DB** (cache: dakika 15 kwa mtumiaji, dakika 5 kwa ROOT). Ukibadilisha role, kuzuia, au kufuta mtu — inaanza kufanya kazi papo hapo bila kusubiri token iishe muda.
- **ROOT ni flag (`users.is_root`), si jina la role.** Hana uanachama wowote. Anatengenezwa na `RbacSeedService` kutoka environment variables pekee, na **analazimishwa** kubadilisha password mara ya kwanza — si onyo tu: `JwtAuthFilter` inamzuia kila mahali hadi abadilishe. `is_root` yenyewe inasomwa **kutoka DB**, si kutoka claim ya token, hivyo kuiondoa kunafanya kazi papo hapo.
- **ROOT akisahau password yake**: hana mtu wa juu yake wa kumsaidia, na akaunti yake huenda haina namba inayopokea SMS. Njia ya kumrudisha ni **environment variable, si endpoint** — inayoifikia ni mwenye ufikiaji wa server, si mtu yeyote mwenye mtandao:

  ```
  ROOT_PASSWORD=<mpya>   ROOT_PASSWORD_RESET=true   # kisha anzisha app upya MARA MOJA
  ```

  Seeding inarudisha password ya ROOT **aliyepo** kuwa `ROOT_PASSWORD` na kuweka `must_change_password = true`, hivyo analazimika kuibadilisha akiingia. **Ondoa `ROOT_PASSWORD_RESET` mara tu baada ya kuitumia** — ikiachwa, kila restart itafuta password aliyoiweka mwenyewe. Bila swichi hiyo (default), seeding ni *create-if-not-exists*: ROOT aliyepo haguswi kabisa.
- **Idhini inadhibitiwa na RUHUSA (`approve_users`), si jina la role** — role yoyote iliyopewa ruhusa hiyo inaweza kuidhinisha.
- Permissions zinapakiwa kutoka `seed/permissions.csv` (idempotent). Role↔permission zinapakiwa kutoka `seed/role_permissions.csv` **mara moja tu kwa role isiyo na ruhusa yoyote** — role iliyobadilishwa na admin haiguswi tena kwenye restart. Kwa hiyo **ruhusa mpya kwa role zilizopo lazima ziongezwe kwa migration**, si kwa CSV pekee (angalia `V7__auth_permissions.sql`).

## Kuendesha

Mahitaji: JDK 17+, Maven, PostgreSQL.

**Siri za LAZIMA** — app haitaanza bila hizi:

| Variable | Maana |
|---|---|
| `DB_PASSWORD` | password ya PostgreSQL |
| `JWT_SECRET` | siri ndefu ya nasibu (angalau herufi 32) |

Za hiari lakini muhimu: `ROOT_PHONE`, `ROOT_PASSWORD`, `ROOT_EMAIL` — zisipowekwa, **ROOT hatengenezwi** (app inaanza, lakini kwa onyo kwenye logs).

Nakili `.env.example` kuwa `.env`, jaza thamani, kisha:

```powershell
mvn spring-boot:run
```

`.env` inasomwa na app yenyewe kupitia `spring.config.import` (angalia `application.yml`), hivyo kitufe cha **Run** cha IDE nacho kinafanya kazi bila usanidi wowote wa ziada. Faili hiyo iko kwenye `.gitignore`.

Bila `.env` wala environment variables, kosa ni:

```
Could not resolve placeholder 'JWT_SECRET' in value "${JWT_SECRET}"
```

Kwenye production hakuna `.env` — weka **environment variables halisi**, ambazo zina kipaumbele zaidi ya faili hiyo.

Flyway inaendesha migrations kiotomatiki. GraphiQL: `http://localhost:8082/graphiql`.

## Majaribio (integration harness)

```powershell
mvn test
```

Yanahitaji **PostgreSQL halisi** yenye haki ya `CREATE/DROP DATABASE` — siri zinatoka `.env` ile ile (au environment variables). Hakuna Docker wala Testcontainers.

Kila run:

1. inatengeneza database yake ya kutupwa, `samaki_test_<nasibu>`;
2. Flyway inaendesha **V1 → ya mwisho kutoka utupu** ndani yake — ndicho kinachofanya migrations kuwa kitu *kinachojaribiwa*, si kinachotumainiwa;
3. fixture ya `@Profile("dev")` (`DevSeedService`) inapandwa — ndiyo faida ya wahusika wa dev kuwa wa kudumu;
4. database inafutwa mwishoni.

**Kutenganisha tests ni kujenga schema upya (Flyway `clean` + `migrate`) kabla ya KILA test**, si `@Transactional` rollback: haya ni majaribio ya HTTP kamili, hivyo server inafungua na kufunga transaction zake mwenyewe — rollback ya test isingegusa chochote alichoandika, na ingeonekana kama imetenganisha ilhali haijatenganisha.

`spring.flyway.clean-disabled=false` iko kwenye profile ya **majaribio pekee**, na `IntegrationTest.guardTestDatabase()` inakataa kusafisha database yoyote ambayo jina lake halianzi na `samaki_test_`.

### Sera: module mpya HAIKAMILIKI bila tests zake

Tangu sasa, kila module inayoongezwa inakuja na tests zake kwenye harness hii. Curl na SQL za mkono zinathibitisha **wakati mmoja**; hazizuii chochote kisirudi. Daily Tasks ndiyo ya kwanza inayoshikwa na sheria hii.

## Documents za schema

`Data_Dictionary_Majedwali.md` na `ERD_Muundo_wa_Database.mermaid` **hazihaririwi kwa mkono** — zinazalishwa kutoka database halisi:

```powershell
$env:PGPASSWORD = "..."; ./tools/generate-docs.ps1
```

Ziendeshe baada ya kila migration. Hii ndiyo inayozuia drift kama ile ya awali ("Data Dictionary inasema 17, ERD inasema 20").

## MUHIMU — Lombok + JDK ya kisasa kwenye Maven

Lombok 1.18.34 (iliyosimamiwa na spring-boot-dependencies 3.3.2) ilikuwa ikishindwa **kimya kimya** kutengeneza getters/setters wakati wa `mvn compile` kwenye JDK 23 — makosa ya "cannot find symbol" kila mahali bila kosa lolote la Lombok lenyewe. Chanzo: "auto-discovery" ya annotation processor kutoka kwenye `-classpath` ndefu kwenye njia yenye nafasi (`D:\KAMPUNI PROJECT\...`). Suluhisho (tayari kwenye `pom.xml`): `<lombok.version>1.18.42</lombok.version>` + `<annotationProcessorPaths>` ya wazi.

## Bado Haijaandikwa

- Entities/resolvers za: FeedPurchase, FeedingLog, FeedStockMovement, WaterQualityLog, TaskCompletion, Reminder, Cost, Customer, Sale, Asset
- API za `species` na kubadilisha jina/mahali pa shamba
- Kubadilisha shamba (farm switching) — muundo unaruhusu uanachama mwingi, lakini token inabeba shamba MOJA (angalia `// TODO: farm switching`)
- Reminders scheduler (Spring `@Scheduled`)
- Angular frontend (haijaguswa)

Angalia `GAP_ANALYSIS.md` kwa uchambuzi kamili wa kilichopo dhidi ya kinachotakiwa.

## Kabla ya production

- Zima GraphiQL (`spring.graphql.graphiql.enabled: false`)
- Badilisha `LoggingSmsSender` (stub inayoandika kwenye logs) na provider halisi — **kwa sasa OTP HAITUMWI kweli**
- Ongeza `spring-boot-starter-actuator` au ondoa ruhusa ya `/actuator/health` kwenye `SecurityConfig`
- Rate limiting ya sasa iko kwenye kumbukumbu ya instance moja — kwa instance nyingi inahitajika Redis au sawa
- Hakikisha load balancer inaandika upya `X-Forwarded-For` (angalia `ClientIp`)

## AWS Deployment (baadaye)

Package kama Docker image (JAR ya Spring Boot + JRE 17 base image), deploy kwenye ECS Fargate — RDS PostgreSQL kama database.
