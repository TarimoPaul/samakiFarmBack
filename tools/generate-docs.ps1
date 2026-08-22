# ============================================================
# Inazalisha Data_Dictionary_Majedwali.md na ERD_Muundo_wa_Database.mermaid
# KUTOKA KWENYE DATABASE HALISI (si kwa mkono).
#
# Hii ndiyo sababu drift ya "17 dhidi ya 20" haitajirudia: documents
# haziandikwi tena kwa mkono - zinazalishwa upya baada ya kila migration.
#
# Matumizi (kutoka root ya backend):
#   $env:PGPASSWORD = "..."; ./tools/generate-docs.ps1
#
# MUHIMU: file hii ina herufi za Kiswahili, hivyo LAZIMA ihifadhiwe kama
# UTF-8 YENYE BOM - PowerShell 5.1 inasoma .ps1 kama ANSI bila BOM, na
# herufi zisizo za ASCII zinaharibu parsing.
# ============================================================
param(
    [string]$DbHost = $(if ($env:DB_HOST) { $env:DB_HOST } else { "localhost" }),
    [string]$DbPort = $(if ($env:DB_PORT) { $env:DB_PORT } else { "5432" }),
    [string]$DbName = $(if ($env:DB_NAME) { $env:DB_NAME } else { "samakiFarm" }),
    [string]$DbUser = $(if ($env:DB_USER) { $env:DB_USER } else { "postgres" })
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Query([string]$sql) {
    $rows = & psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -t -A -F "|" -c $sql
    if ($LASTEXITCODE -ne 0) { throw "psql imeshindwa" }
    return @($rows | Where-Object { $_ -and $_.Trim() -ne "" })
}

# Kila safu inakuwa OBJECT, si array. Hii ni ya lazima: PowerShell inafumua
# array yenye kipengele kimoja, hivyo jedwali lenye FK MOJA tu lingepoteza
# uhusiano wake kimyakimya (assets/costs/feed_purchases zilipotea hivyo).
function ToObjects($rows, [string[]]$fields) {
    $out = @()
    foreach ($r in $rows) {
        $parts = $r -split '\|', $fields.Count
        $o = [ordered]@{}
        for ($i = 0; $i -lt $fields.Count; $i++) {
            $o[$fields[$i]] = if ($i -lt $parts.Count) { $parts[$i] } else { "" }
        }
        $out += [pscustomobject]$o
    }
    return $out
}

# ---------- Kusoma schema halisi ----------
$columns = ToObjects (Query @'
SELECT c.table_name, c.column_name,
       CASE WHEN c.data_type='character varying' THEN 'varchar(' || c.character_maximum_length || ')'
            WHEN c.data_type='numeric' AND c.numeric_precision IS NOT NULL
                 THEN 'numeric(' || c.numeric_precision || ',' || c.numeric_scale || ')'
            WHEN c.data_type='timestamp with time zone' THEN 'timestamptz'
            WHEN c.data_type='time without time zone' THEN 'time'
            ELSE c.data_type END,
       c.is_nullable, COALESCE(c.column_default,''), c.is_generated
FROM information_schema.columns c
JOIN information_schema.tables t
  ON t.table_name=c.table_name AND t.table_schema=c.table_schema
WHERE c.table_schema='public' AND t.table_type='BASE TABLE'
  AND c.table_name <> 'flyway_schema_history'
ORDER BY c.table_name, c.ordinal_position;
'@) @("Table","Column","Type","Nullable","Default","Generated")

$constraints = ToObjects (Query @'
SELECT con.conrelid::regclass::text, con.contype::text, con.conname,
       replace(replace(pg_get_constraintdef(con.oid), chr(10), ' '), '|', '/')
FROM pg_constraint con
JOIN pg_class rel ON rel.oid = con.conrelid
JOIN pg_namespace n ON n.oid = rel.relnamespace
WHERE n.nspname='public' AND rel.relname <> 'flyway_schema_history'
ORDER BY 1, 2 DESC, 3;
'@) @("Table","Type","Name","Def")

$indexes = ToObjects (Query @'
SELECT tablename, indexname, replace(indexdef, '|', '/')
FROM pg_indexes
WHERE schemaname='public' AND tablename <> 'flyway_schema_history'
ORDER BY tablename, indexname;
'@) @("Table","Name","Def")

$migrationSql = "SELECT version, description, installed_on::date FROM flyway_schema_history WHERE success ORDER BY installed_rank;"
$migrations = ToObjects (Query $migrationSql) @("Version","Description","Installed")

$tables = @($columns | Select-Object -ExpandProperty Table -Unique | Sort-Object)

# Vikundi vya ERD. Jedwali lisilotajwa linaingia "Nyingine" - hivyo jedwali
# jipya HALIWEZI kupotea kimyakimya kwenye document.
$groups = [ordered]@{
    "1. RBAC / UAA"                  = @("users","roles","permissions","role_permissions","password_reset_otps")
    "2. Mashamba na uanachama"       = @("farms","farm_users")
    "3. Rasilimali na vitengo"       = @("production_units","assets")
    "4. Aina za samaki na mizunguko" = @("species","cycles")
    "5. Chakula"                     = @("feed_purchases","feeding_logs","feed_stock_movements")
    "6. Ubora wa maji"               = @("water_quality_logs")
    "7. Kazi na vikumbusho"          = @("daily_tasks","task_completions","reminders")
    "8. Fedha"                       = @("costs","sales","customers")
}
$grouped = @($groups.Values | ForEach-Object { $_ })
$ungrouped = @($tables | Where-Object { $grouped -notcontains $_ })
if ($ungrouped.Count -gt 0) { $groups["9. Nyingine (hazijapangwa)"] = $ungrouped }

$today = Get-Date -Format "yyyy-MM-dd"

function ColumnsOf([string]$t)     { return @($columns     | Where-Object { $_.Table -eq $t }) }
function ConstraintsOf([string]$t) { return @($constraints | Where-Object { $_.Table -eq $t }) }
function ForeignKeysOf([string]$t) { return @($constraints | Where-Object { $_.Table -eq $t -and $_.Type -eq 'f' }) }

# ============================================================
# 1. Data Dictionary
# ============================================================
$dd = [System.Text.StringBuilder]::new()
[void]$dd.AppendLine("# Data Dictionary - Majedwali ya Mfumo wa Ufugaji wa Samaki")
[void]$dd.AppendLine()
[void]$dd.AppendLine("> **IMEZALISHWA KIOTOMATIKI - USIIHARIRI KWA MKONO.**")
[void]$dd.AppendLine("> Chanzo: database halisi. Izalishe upya baada ya kila migration:")
[void]$dd.AppendLine("> ``./tools/generate-docs.ps1``")
[void]$dd.AppendLine(">")
[void]$dd.AppendLine("> Toleo la $today. Jedwali: **$($tables.Count)**. Safu: **$($columns.Count)**.")
[void]$dd.AppendLine()
[void]$dd.AppendLine("## Migrations zilizotumika")
[void]$dd.AppendLine()
foreach ($m in $migrations) {
    [void]$dd.AppendLine("- **V$($m.Version)** - $($m.Description) _($($m.Installed))_")
}
[void]$dd.AppendLine()
[void]$dd.AppendLine("## Muhtasari")
[void]$dd.AppendLine()
[void]$dd.AppendLine("| # | Kikundi | Jedwali |")
[void]$dd.AppendLine("|---|---------|---------|")
$i = 0
foreach ($g in $groups.Keys) {
    $present = @($groups[$g] | Where-Object { $tables -contains $_ })
    if ($present.Count -eq 0) { continue }
    $i++
    $list = ($present | ForEach-Object { '`' + $_ + '`' }) -join ', '
    [void]$dd.AppendLine("| $i | $g | $list |")
}
[void]$dd.AppendLine()

foreach ($g in $groups.Keys) {
    $present = @($groups[$g] | Where-Object { $tables -contains $_ })
    if ($present.Count -eq 0) { continue }
    [void]$dd.AppendLine("---")
    [void]$dd.AppendLine()
    [void]$dd.AppendLine("## $g")
    [void]$dd.AppendLine()
    foreach ($t in $present) {
        [void]$dd.AppendLine("### ``$t``")
        [void]$dd.AppendLine()
        [void]$dd.AppendLine("| Safu | Aina | Null | Default |")
        [void]$dd.AppendLine("|------|------|------|---------|")
        foreach ($c in (ColumnsOf $t)) {
            $nullFlag = if ($c.Nullable -eq 'NO') { "NOT NULL" } else { "" }
            $def = $c.Default -replace '::[a-z ]+', ''
            if ($c.Generated -eq 'ALWAYS') { $def = "GENERATED" }
            [void]$dd.AppendLine("| ``$($c.Column)`` | $($c.Type) | $nullFlag | $def |")
        }
        [void]$dd.AppendLine()

        $cons = ConstraintsOf $t
        if ($cons.Count -gt 0) {
            [void]$dd.AppendLine("**Vikwazo:**")
            [void]$dd.AppendLine()
            foreach ($k in $cons) {
                $kind = switch ($k.Type) { "p" { "PK" } "f" { "FK" } "u" { "UNIQUE" } "c" { "CHECK" } default { $k.Type } }
                [void]$dd.AppendLine("- **$kind** ``$($k.Name)`` - $($k.Def)")
            }
            [void]$dd.AppendLine()
        }

        $consNames = @($cons | Select-Object -ExpandProperty Name)
        $extraIdx = @($indexes | Where-Object { $_.Table -eq $t -and $consNames -notcontains $_.Name })
        if ($extraIdx.Count -gt 0) {
            [void]$dd.AppendLine("**Index:**")
            [void]$dd.AppendLine()
            foreach ($x in $extraIdx) { [void]$dd.AppendLine("- ``$($x.Name)``") }
            [void]$dd.AppendLine()
        }
    }
}

[System.IO.File]::WriteAllText((Join-Path $repoRoot "Data_Dictionary_Majedwali.md"), $dd.ToString(), $utf8NoBom)

# ============================================================
# 2. ERD (mermaid)
# ============================================================
$erd = [System.Text.StringBuilder]::new()
[void]$erd.AppendLine("%% ERD ya Mfumo wa Ufugaji wa Samaki")
[void]$erd.AppendLine("%% IMEZALISHWA KIOTOMATIKI kutoka database halisi - USIIHARIRI KWA MKONO.")
[void]$erd.AppendLine("%% Izalishe upya: ./tools/generate-docs.ps1   ($today, jedwali $($tables.Count))")
[void]$erd.AppendLine("erDiagram")

foreach ($t in $tables) {
    foreach ($k in (ForeignKeysOf $t)) {
        if ($k.Def -match 'FOREIGN KEY \((.+?)\) REFERENCES ([a-z_]+)\(') {
            $col = $matches[1]
            $target = $matches[2]
            # updated_by/deleted_by ni audit - zingejaza mchoro kwa kelele
            if ($target -eq $t -or $col -in @('updated_by','deleted_by')) { continue }
            [void]$erd.AppendLine("    $target ||--o{ ${t} : `"$col`"")
        }
    }
}
[void]$erd.AppendLine()

foreach ($t in $tables) {
    [void]$erd.AppendLine("    $t {")
    $pkCols = @()
    $pk = @(ConstraintsOf $t | Where-Object { $_.Type -eq 'p' })
    if ($pk.Count -gt 0 -and $pk[0].Def -match 'PRIMARY KEY \((.+?)\)') {
        $pkCols = @($matches[1] -split ',\s*')
    }
    $fkCols = @()
    foreach ($k in (ForeignKeysOf $t)) {
        if ($k.Def -match 'FOREIGN KEY \((.+?)\)') { $fkCols += @($matches[1] -split ',\s*') }
    }
    foreach ($c in (ColumnsOf $t)) {
        # mermaid haikubali nafasi/mabano kwenye aina
        $type = $c.Type -replace '[()\s,]', '_'
        $key = ""
        if ($pkCols -contains $c.Column) { $key = " PK" }
        elseif ($fkCols -contains $c.Column) { $key = " FK" }
        [void]$erd.AppendLine("        $type $($c.Column)$key")
    }
    [void]$erd.AppendLine("    }")
}

[System.IO.File]::WriteAllText((Join-Path $repoRoot "ERD_Muundo_wa_Database.mermaid"), $erd.ToString(), $utf8NoBom)

$relCount = ([regex]::Matches($erd.ToString(), '\|\|--o\{')).Count
Write-Output "Zimezalishwa kutoka database halisi:"
Write-Output "  Data_Dictionary_Majedwali.md      (jedwali $($tables.Count), safu $($columns.Count), vikwazo $($constraints.Count))"
Write-Output "  ERD_Muundo_wa_Database.mermaid    (uhusiano $relCount)"
if ($ungrouped.Count -gt 0) { Write-Warning "Jedwali hazijapangwa kwenye kikundi: $($ungrouped -join ', ')" }
