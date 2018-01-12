#!/usr/bin/ruby
require 'my_gem'
require 'rspec'
require 'nokogiri'

Gem.loaded_specs.values.each do |spec|
    puts "Name: " + spec.name
    puts "\tSummary: " + spec.summary
    puts "\tDescription: " + spec.description
    puts "\tAuthor: " + spec.author
    puts "\tPath: " + spec.full_gem_path
    puts "\tVersion: " + spec.version.to_s
    puts "\tRequirements: " + spec.requirements.to_s
    puts "\tLicences: " + spec.licenses.to_s
    puts "\tPlatform: " + spec.platform.to_s
    puts "\tDependencies: "
    spec.dependencies.each do |dep|
        puts "\t\t- "+ dep.name
    end
    puts "========================================="
    # print spec.version + "\n"
end
