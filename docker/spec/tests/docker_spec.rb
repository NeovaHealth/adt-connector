require "spec_helper"

describe "ADT Connector Docker image" do
  before(:all) do
    set :os, family: :debian
    set :backend, :docker
    set :docker_image, image
  end

  # Java installed.
  it "installs the right version of java" do
    java_version = command("dpkg -l oracle-java8-installer").stdout
    expect(java_version).to include(" 8u")
  end

  # Karaf installed.
  describe file('/opt/karaf/') do
    it { should exist }
  end

  # Correct Karaf version is installed.
  describe command('printenv | grep KARAF_VERSION') do
    its(:stdout) { should match /KARAF_VERSION=3.0.4/ }
  end

  # ADT connector KAR file in the deploy directory.
  describe file('/deploy/nhadt_2.10-0.2.5.kar') do
    it { should exist }
  end

  # ADT connector config files in Karaf's config directory.
  describe file('/opt/karaf/etc/nh/uk.co.neovahealth.nhADT.conf') do
    it { should exist }
  end

  describe file('/opt/karaf/etc/nh/uk.co.neovahealth.nhADT.properties') do
    it { should exist }
  end

  describe file('/opt/karaf/etc/nh/uk.co.neovahealth.nhADT.rules') do
    it { should exist }
  end

  # Karaf service is running.
  # The square brackets in the grep string are a hack to omit the grep command from the ps output.
  describe command('ps -Af | grep "[o]rg.apache.karaf.main.Main" | wc -l') do
    its(:stdout) { should match /1/ }
  end

end
