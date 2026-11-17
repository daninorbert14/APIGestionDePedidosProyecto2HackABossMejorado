# T4 — Checklist de conformidad de .github/dependabot.yml contra specs/007-activar-dependabot/spec.md
# Uso: ruby specs/007-activar-dependabot/verificacion_t4.rb
# Devuelve una tabla punto -> resultado real y sale con código 1 si algo falla.
require "yaml"

ARCHIVO = ".github/dependabot.yml"
texto = File.read(ARCHIVO)
doc = YAML.safe_load(texto) # parser local (librería estándar de Ruby)

resultados = []
fallos = 0
guardar = lambda do |id, rf, ok, detalle|
  resultados << [id, rf, ok ? "PASS" : "FAIL", detalle]
  fallos += 1 unless ok
end

# --- 1. YAML válido y estructura básica --------------------------------------
yaml_ok = doc.is_a?(Hash) && doc["version"] == 2 && doc["updates"].is_a?(Array)
entradas = yaml_ok ? doc["updates"] : []
guardar.call(1, "YAML válido", yaml_ok,
             yaml_ok ? "version=2, #{entradas.size} entradas parseadas" : "estructura inesperada")

# --- 2. 5 ecosistemas en 6 entradas con directorios correctos (RF-1, RF-2) ----
esperado = [
  ["npm", "/Frontend"],
  ["maven", "/Backend"],
  ["github-actions", "/"],
  ["docker", "/Backend"],
  ["docker", "/Frontend"],
  ["docker-compose", "/"],
]
real = entradas.map { |e| [e["package-ecosystem"], e["directory"]] }
ok_rf1_rf2 = real == esperado && entradas.size == 6 &&
             entradas.map { |e| e["package-ecosystem"] }.uniq.size == 5
guardar.call(2, "RF-1, RF-2", ok_rf1_rf2,
             "real: #{real.inspect}")

# --- 3. Sin groups: un PR por dependencia (RF-3) -----------------------------
con_groups = entradas.each_index.select { |i| entradas[i].key?("groups") }
guardar.call(3, "RF-3", con_groups.empty?,
             con_groups.empty? ? "ninguna entrada declara 'groups'" : "con 'groups': #{con_groups.inspect}")

# --- 4. schedule lunes 09:00 UTC en cada entrada (RF-4) ---------------------
sched = entradas.map do |e|
  s = e["schedule"] || {}
  [s["interval"], s["day"], s["time"], s["timezone"]]
end
ok_sched = sched.size == 6 && sched.all? { |x| x == ["weekly", "monday", "09:00", "UTC"] }
guardar.call(4, "RF-4", ok_sched,
             ok_sched ? "6/6 entradas: weekly / monday / 09:00 / UTC" : sched.inspect)

# --- 5. cooldown de 7 días en las seis entradas (RF-4b) ----------------------
cd = entradas.map { |e| e.dig("cooldown", "default-days") }
ok_cd = cd.all? { |v| v == 7 } && cd.size == 6
guardar.call(5, "RF-4b", ok_cd,
             "default-days: #{cd.inspect} (todas admiten la opción en el archivo)")

# --- 6. open-pull-requests-limit: 3 en las seis entradas (RF-5) --------------
lim = entradas.map { |e| e["open-pull-requests-limit"] }
ok_lim = lim.all? { |v| v == 3 } && lim.size == 6
guardar.call(6, "RF-5", ok_lim, "límites: #{lim.inspect}")

# --- Helpers por entrada -----------------------------------------------------
por_eco_dir = ->(eco, dir) { entradas.find_index { |e| e["package-ecosystem"] == eco && e["directory"] == dir } }
ignores_de = ->(i) { (entradas[i]["ignore"] || []) }

# --- 7. postgres completo en las tres entradas de imagen (RF-6) -------------
tres = [["docker", "/Backend"], ["docker", "/Frontend"], ["docker-compose", "/"]]
det_rf6 = tres.map do |eco, dir|
  i = por_eco_dir.call(eco, dir)
  ign = i ? ignores_de.call(i) : nil
  pg = ign&.find { |x| x["dependency-name"] == "postgres" }
  ["/#{dir.sub(%r{^/}, '')} #{eco}", pg ? (pg.key?("update-types") ? "FAIL(update-types)" : "sin update-types OK") : "FAIL(sin ignore)"]
end
ok_rf6 = tres.all? { |eco, dir|
  i = por_eco_dir.call(eco, dir)
  pg = i && ignores_de.call(i).find { |x| x["dependency-name"] == "postgres" }
  pg && !pg.key?("update-types")
}
guardar.call(7, "RF-6", ok_rf6, det_rf6.map { |a, b| "#{a}: #{b}" }.join(" | "))

# --- 8. Solo mayores de node / eclipse-temurin / maven en su entrada (RF-7) -
esper_rf7 = {
  ["docker", "/Backend"] => { "maven" => "version-update:semver-major", "eclipse-temurin" => "version-update:semver-major" },
  ["docker", "/Frontend"] => { "node" => "version-update:semver-major" },
}
todos_ignores = []
entradas.each_with_index do |e, i|
  ignores_de.call(i).each do |x|
    todos_ignores << { eco: e["package-ecosystem"], dir: e["directory"], nombre: x["dependency-name"], tipos: x["update-types"] }
  end
end
faltantes = []
esper_rf7.each do |(eco, dir), mapa|
  mapa.each do |nombre, tipo|
    en = todos_ignores.select { |x| x[:eco] == eco && x[:dir] == dir && x[:nombre] == nombre }
    # cada ignore de estos nombres debe llevar exactamente ese update-type
    faltantes << "#{nombre} ausente en #{eco} #{dir}" if en.empty?
    faltantes << "#{nombre} mal tipado en #{eco} #{dir}" unless en.all? { |x| x[:tipos] == [tipo] }
  end
end
# y solo donde su imagen aparece: ninguno de los tres nombres fuera de su entrada
malos = todos_ignores.select do |x|
  case x[:nombre]
  when "maven", "eclipse-temurin" then !(x[:eco] == "docker" && x[:dir] == "/Backend")
  when "node" then !(x[:eco] == "docker" && x[:dir] == "/Frontend")
  else false
  end
end
otros_nombres = todos_ignores.map { |x| x[:nombre] } - %w[postgres maven eclipse-temurin node]
ok_rf7 = malos.empty? && otros_nombres.empty? && faltantes.empty?
guardar.call(8, "RF-7", ok_rf7,
             "ignores de imagen: #{todos_ignores.map { |x| "#{x[:nombre]}@#{x[:eco]}#{x[:dir]}=#{(x[:tipos] || ['(ninguno)']).join(',')}" }.join(' | ')}#{faltantes.empty? ? '' : ' | FALTAN: ' + faltantes.join(', ')}")

# --- 9. Cero ignores de ESLint o minimatch (RF-8) ---------------------------
búsqueda = texto.scan(/eslint|minimatch/i)
guardar.call(9, "RF-8", búsqueda.empty?,
             búsqueda.empty? ? "búsqueda case-insensitive 'eslint|minimatch' en el fichero: 0 resultados" : "encontrado: #{búsqueda.inspect}")

# --- 10. Comentarios en español y cada ignore con motivo y cuándo retirar (RF-9)
#        (comprobación programática parcial + lectura revisada en el informe)
comentarios = texto.lines.select { |l| l.strip.start_with?("#") }
no_es = comentarios.reject { |l| l =~ /\b(el|la|los|las|de|del|que|si|solo|un|una|y|en|con|las|los|retirar|proyecto|imagen|mayores|RF-|especific|fijad|coincid|pruebas|dependabot|especifica|actualiza|cualquier|nivel|hoy|futuro|cambio|sube|node|java|postgres)\b/i }
guardar.call(10, "RF-9 (parcial programático)", no_es.empty?,
             "#{comentarios.size} comentarios revisados; sin términos sospechosos de otro idioma. Detalle en el informe.")

puts "=" * 78
puts "CHECKLIST T4 — #{ARCHIVO}"
puts "=" * 78
puts format("%-4s %-22s %-6s %s", "#", "RF", "OK?", "Detalle")
resultados.each { |id, rf, ok, det| puts format("%-4s %-22s %-6s %s", id, rf, ok, det) }
puts "-" * 78
puts fallos.zero? ? "RESULTADO: 10/10 PASS" : "RESULTADO: #{fallos} FALLO(S)"
exit(fallos.zero? ? 0 : 1)
