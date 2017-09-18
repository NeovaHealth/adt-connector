require "serverspec"
require "docker"

def image
  version = ENV['VERSION'] || 1
  "adtconnector:#{version}"
end
