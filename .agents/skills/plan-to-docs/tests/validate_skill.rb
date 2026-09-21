#!/usr/bin/env ruby

require "set"
require "yaml"

def fail_validation(message)
  warn "FAIL: #{message}"
  exit 1
end

skill_dir = ARGV.fetch(0) do
  fail_validation("Usage: validate_skill.rb <skill-directory>")
end

skill_path = File.join(skill_dir, "SKILL.md")
ui_path = File.join(skill_dir, "agents", "openai.yaml")
reference_path = File.join(skill_dir, "references", "output-format.md")

[skill_path, ui_path, reference_path].each do |path|
  fail_validation("Missing required file: #{path}") unless File.file?(path)
end

skill_text = File.read(skill_path)
frontmatter_match = skill_text.match(/\A---\n(.*?)\n---/m)
fail_validation("SKILL.md has invalid frontmatter") unless frontmatter_match

frontmatter = YAML.safe_load(frontmatter_match[1])
fail_validation("SKILL.md frontmatter must be a mapping") unless frontmatter.is_a?(Hash)

allowed_keys = Set.new(%w[name description license allowed-tools metadata])
unexpected_keys = frontmatter.keys.to_set - allowed_keys
unless unexpected_keys.empty?
  fail_validation("Unexpected frontmatter keys: #{unexpected_keys.to_a.sort.join(', ')}")
end

name = frontmatter["name"]
unless name == "plan-to-docs" && name.match?(/\A[a-z0-9-]+\z/) && name.length <= 64
  fail_validation("Invalid skill name")
end

description = frontmatter["description"]
unless description.is_a?(String) && !description.empty? && description.length <= 1024
  fail_validation("Invalid skill description")
end

ui = YAML.safe_load(File.read(ui_path))
unless ui.dig("policy", "allow_implicit_invocation") == false
  fail_validation("plan-to-docs must require explicit invocation")
end

short_description = ui.dig("interface", "short_description")
unless short_description.is_a?(String) && (25..64).cover?(short_description.length)
  fail_validation("UI short_description must contain 25-64 characters")
end

default_prompt = ui.dig("interface", "default_prompt")
unless default_prompt.is_a?(String) && default_prompt.include?("$plan-to-docs")
  fail_validation("UI default_prompt must explicitly invoke $plan-to-docs")
end

unless skill_text.include?("[references/output-format.md](references/output-format.md)")
  fail_validation("SKILL.md must link to the output-format reference")
end

reference_text = File.read(reference_path)
unless reference_text.include?("<!-- plan-to-docs-document: <document-id> -->") &&
       reference_text.include?("<!-- plan-to-docs:<document-id>:<slice-id> -->")
  fail_validation("Output format must define stable document and issue markers")
end

Dir.glob(File.join(skill_dir, "**", "*")).select { |path| File.file?(path) }.each do |path|
  next unless [".md", ".yaml", ".yml", ".rb", ".sh", ".py"].include?(File.extname(path))

  contents = File.binread(path)
  fail_validation("Missing final newline: #{path}") unless contents.end_with?("\n")
  contents.lines.each_with_index do |line, index|
    if line.match?(/[ \t]+\n\z/)
      fail_validation("Trailing whitespace: #{path}:#{index + 1}")
    end
  end
  fail_validation("Unfinished TODO placeholder: #{path}") if contents.match?(/^ {0,3}\[TODO:/)
end

puts "Static plan-to-docs validation passed."
