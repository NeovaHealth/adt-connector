require "serverspec"
require "docker"

def image
  label = ENV['GO_PIPELINE_LABEL'] || 1
  counter = ENV['GO_STAGE_COUNTER'] || 1
  version = "#{label}-#{counter}"
  "adtconnector:#{version}"
end
